package org.companerodeescuela

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.MfaRepository
import org.companerodeescuela.core.network.createApiClient
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.admin.AdminRepository
import org.companerodeescuela.feature.admin.AdminViewModel
import org.companerodeescuela.feature.admin.superAdminHistoryScreen
import org.companerodeescuela.feature.auth.AuthRepository
import org.companerodeescuela.feature.auth.InvitationIssueScreen
import org.companerodeescuela.feature.auth.InvitationIssueViewModel
import org.companerodeescuela.shared.contracts.AdminAuditEntry
import org.companerodeescuela.shared.contracts.AdminUserSummary
import org.companerodeescuela.shared.contracts.AdministrationMetrics
import org.companerodeescuela.shared.contracts.AdministrationOverview
import org.companerodeescuela.shared.contracts.ApiError
import org.companerodeescuela.shared.contracts.ApiErrorCode
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AuthorizeRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.MfaStepUpRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRetryRequest
import org.companerodeescuela.shared.contracts.PrivilegedCommandDigests
import org.companerodeescuela.shared.contracts.RegistrationInvitationDispatchReceipt
import org.companerodeescuela.shared.contracts.RegistrationInvitationPreparation
import org.companerodeescuela.shared.contracts.RegistrationInvitationSummary
import org.companerodeescuela.shared.contracts.RetryRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.StepUpProof
import org.companerodeescuela.shared.contracts.TutorAssignmentSummary
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instrumented Compose + HTTP contract verification for Super Admin invitation issuance,
 * visible institution load failure/retry, webhook delivery status differentiation,
 * MFA-bound invitation retry, and post-acceptance profile authorization.
 */
class SuperAdminInvitationFlowUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class SuperAdminStore : SessionTokenStore {
        private val current =
            MutableStateFlow<String?>(
                "e30." +
                    Base64.getUrlEncoder().withoutPadding().encodeToString(
                        """{"sub":"qa-owner","session_id":"qa-owner-session","roles":["SUPER_ADMIN"],"exp":4102444800}"""
                            .toByteArray(),
                    ) +
                    ".sig",
            )

        override suspend fun readAccessToken(): String? = current.value

        override suspend fun writeAccessToken(token: String) {
            current.value = token
        }

        override suspend fun clear() {
            current.value = null
        }

        override fun observeAccessToken(): Flow<String?> = current
    }

    private fun waitTag(
        tag: String,
        timeoutMillis: Long = 10_000,
    ) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitText(
        text: String,
        timeoutMillis: Long = 10_000,
    ) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun typeExactly(
        tag: String,
        value: String,
        scroll: Boolean = true,
    ) {
        val node =
            if (scroll) {
                compose.onNodeWithTag(tag).performScrollTo()
            } else {
                compose.onNodeWithTag(tag)
            }
        var observed = ""
        repeat(3) {
            node.performTextClearance()
            node.performTextInput(value)
            compose.waitForIdle()
            val config = node.fetchSemanticsNode().config
            observed =
                if (config.contains(SemanticsProperties.EditableText)) {
                    config[SemanticsProperties.EditableText].text
                } else {
                    value
                }
            val maskedMatch =
                value.none { it == '\u2022' } &&
                    observed.all { it == '\u2022' } &&
                    observed.length == value.length
            if (observed == value || maskedMatch) return
        }
        throw AssertionError("Field $tag kept value \"$observed\" instead of \"$value\"")
    }

    @Test
    fun institutionLoadErrorShowsVisibleFailureAndRetriesToIssueInvitation() {
        val store = SuperAdminStore()
        val institutionCalls = AtomicInteger(0)
        val stepUps = CopyOnWriteArrayList<MfaStepUpRequest>()
        val dispatches = CopyOnWriteArrayList<PrepareRegistrationInvitationRequest>()
        val expectedDigest = "b".repeat(64)
        val validProof = "P".repeat(43)
        val createdInvitationId = "I".repeat(43)
        val now = Clock.systemUTC().instant().epochSecond

        val client =
            createApiClient(
                ApiEnvironment("https://qa-invites.test/", "instrumented-invite-issue"),
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/institutions" -> {
                            if (institutionCalls.incrementAndGet() == 1) {
                                respond(
                                    Json.encodeToString(
                                        ApiError(
                                            code = ApiErrorCode.DEPENDENCY_UNAVAILABLE,
                                            message = "Directory temporarily unavailable",
                                            requestId = "req-inst-503",
                                        ),
                                    ),
                                    HttpStatusCode.ServiceUnavailable,
                                    headersOf(
                                        HttpHeaders.ContentType to listOf("application/json"),
                                        "X-Request-Id" to listOf("req-inst-503"),
                                    ),
                                )
                            } else {
                                respond(
                                    Json.encodeToString(
                                        ApiResponse(listOf(InstitutionSummary("UPTLAX", "UPTLAX Ingeniería"))),
                                    ),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                        }
                        "/admin/invitations/prepare" -> {
                            val req =
                                Json.decodeFromString<PrepareRegistrationInvitationRequest>(
                                    (request.body as TextContent).text,
                                )
                            respond(
                                Json.encodeToString(
                                    ApiResponse(
                                        RegistrationInvitationPreparation(
                                            operationId = req.operationId,
                                            resourceId = req.institutionId,
                                            payloadDigest = expectedDigest,
                                        ),
                                    ),
                                ),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        "/auth/mfa/step-up" -> {
                            val stepUp = Json.decodeFromString<MfaStepUpRequest>((request.body as TextContent).text)
                            stepUps += stepUp
                            respond(
                                Json.encodeToString(ApiResponse(StepUpProof(validProof, now + 300))),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        "/admin/invitations" -> {
                            val dispatch =
                                Json.decodeFromString<PrepareRegistrationInvitationRequest>(
                                    (request.body as TextContent).text,
                                )
                            dispatches += dispatch
                            respond(
                                Json.encodeToString(
                                    ApiResponse(
                                        RegistrationInvitationDispatchReceipt(
                                            invitationId = createdInvitationId,
                                            delivery = VerificationDeliveryStatus.ACCEPTED,
                                            expiresAtEpochSeconds = now + 86_400,
                                        ),
                                    ),
                                ),
                                HttpStatusCode.Created,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        else -> error("Unexpected path: ${request.url.encodedPath}")
                    }
                },
            )
        val viewModel = InvitationIssueViewModel(AuthRepository(client, store), MfaRepository(client, store))
        try {
            compose.setContent {
                CompaneroTheme {
                    InvitationIssueScreen(onBack = {}, viewModel = viewModel)
                }
            }

            // 1. First load fails -> explicit error notice & retry button, never silent empty state
            waitTag("invite_institutions_error")
            compose.onNodeWithTag("invite_institutions_error").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("invite_institutions_retry").performScrollTo().performClick()

            // 2. Retry succeeds -> institution chip appears and auto-selects single enabled campus
            waitTag("invite_institution_UPTLAX")
            compose.onNodeWithTag("invite_institution_UPTLAX").performScrollTo().assertIsDisplayed()

            // 3. Complete MFA-protected invitation dispatch -> shows provider acceptance (pending webhook delivery)
            typeExactly("invite_recipient", "docente.qa@example.test")
            typeExactly("invite_password", "test-only-cred-123")
            typeExactly("invite_otp", "654321")
            compose.onNodeWithTag("invite_send").performScrollTo().performClick()

            waitTag("invite_delivery")
            compose.onNodeWithText("Aceptada por proveedor (entrega pendiente)").performScrollTo().assertIsDisplayed()
            assertEquals(1, stepUps.size)
            assertEquals(StepUpAction.ADMIN_INVITE, stepUps.single().action)
            assertEquals(1, dispatches.size)
            assertEquals("docente.qa@example.test", dispatches.single().recipient)
            assertEquals(validProof, dispatches.single().proof)
        } finally {
            viewModel.viewModelScope.cancel()
            client.close()
        }
    }

    @Test
    fun superAdminHistoryDifferentiatesWebhookDeliveryRetriesWithMfaAndAuthorizesAcceptedProfile() {
        val store = SuperAdminStore()
        val now = Clock.systemUTC().instant().epochSecond
        val bouncedInviteId = "B".repeat(43)
        val acceptedInviteId = "C".repeat(43)
        val retryDigest = "c".repeat(64)
        val stepUpProof = "Q".repeat(43)
        val retryCalls = CopyOnWriteArrayList<RetryRegistrationInvitationRequest>()
        val authCalls = CopyOnWriteArrayList<AuthorizeRegistrationInvitationRequest>()
        val stepUps = CopyOnWriteArrayList<MfaStepUpRequest>()
        var retried = false
        var authorized = false

        val candidateSummary =
            AdminUserSummary(
                UserSummary(
                    id = "cand-teacher-1",
                    displayName = "Docente Aceptado QA",
                    email = "docente.aceptado@example.test",
                    roles = setOf(UserRole.STUDENT),
                    emailVerified = true,
                    authRevision = 5,
                ),
                emptyList(),
            )

        val client =
            createApiClient(
                ApiEnvironment("https://qa-history.test/", "instrumented-super-admin-history"),
                MockEngine { request ->
                    val body =
                        when (request.url.encodedPath) {
                            "/admin/accounts" ->
                                Json.encodeToString(
                                    ApiResponse(
                                        AdministrationOverview(
                                            listOf(candidateSummary),
                                            AdministrationMetrics(1, 0, if (authorized) 1 else 0, 0, 0),
                                        ),
                                    ),
                                )
                            "/admin/accounts/approval-groups" ->
                                Json.encodeToString(ApiResponse(emptyList<org.companerodeescuela.shared.contracts.AcademicGroupSummary>()))
                            "/tutoring/assignments" ->
                                Json.encodeToString(ApiResponse(emptyList<TutorAssignmentSummary>()))
                            "/admin/audit" ->
                                Json.encodeToString(
                                    ApiResponse(
                                        listOf(
                                            AdminAuditEntry(
                                                id = "aud-webhook-1",
                                                actorId = "RESEND_WEBHOOK",
                                                action = "EMAIL_BOUNCED",
                                                resourceType = "INVITATION",
                                                resourceId = bouncedInviteId,
                                                outcome = "FAILED",
                                                createdAtEpochSeconds = now - 30,
                                            ),
                                            AdminAuditEntry(
                                                id = "aud-webhook-2",
                                                actorId = "RESEND_WEBHOOK",
                                                action = "EMAIL_DELIVERED",
                                                resourceType = "INVITATION",
                                                resourceId = acceptedInviteId,
                                                outcome = "SUCCEEDED",
                                                createdAtEpochSeconds = now - 20,
                                            ),
                                        ),
                                    ),
                                )
                            "/admin/invitations" -> {
                                assertEquals("50", request.url.parameters["limit"])
                                Json.encodeToString(
                                    ApiResponse(
                                        listOf(
                                            RegistrationInvitationSummary(
                                                invitationId = bouncedInviteId,
                                                recipient = "rebote.docente@example.test",
                                                institutionId = "UPTLAX",
                                                profile = "TEACHER",
                                                status = "SENT",
                                                createdAtEpochSeconds = now - 600,
                                                expiresAtEpochSeconds = now + 86_400,
                                                deliveryStatus = if (retried) "ACCEPTED" else "BOUNCED",
                                            ),
                                            RegistrationInvitationSummary(
                                                invitationId = acceptedInviteId,
                                                recipient = "docente.aceptado@example.test",
                                                institutionId = "UPTLAX",
                                                profile = "TEACHER",
                                                status = if (authorized) "ACTIVE" else "ACCEPTED",
                                                createdAtEpochSeconds = now - 900,
                                                expiresAtEpochSeconds = now + 86_400,
                                                acceptedAtEpochSeconds = now - 120,
                                                deliveryStatus = "DELIVERED",
                                                acceptedByAccountId = "cand-teacher-1",
                                            ),
                                        ),
                                    ),
                                )
                            }
                            "/admin/invitations/retry/prepare" -> {
                                val prepReq =
                                    Json.decodeFromString<PrepareRegistrationInvitationRetryRequest>(
                                        (request.body as TextContent).text,
                                    )
                                Json.encodeToString(
                                    ApiResponse(
                                        RegistrationInvitationPreparation(
                                            operationId = prepReq.operationId,
                                            resourceId = prepReq.invitationId,
                                            payloadDigest = retryDigest,
                                        ),
                                    ),
                                )
                            }
                            "/auth/mfa/step-up" -> {
                                val stepUp = Json.decodeFromString<MfaStepUpRequest>((request.body as TextContent).text)
                                stepUps += stepUp
                                Json.encodeToString(ApiResponse(StepUpProof(stepUpProof, now + 300)))
                            }
                            "/admin/invitations/retry" -> {
                                val retryReq =
                                    Json.decodeFromString<RetryRegistrationInvitationRequest>(
                                        (request.body as TextContent).text,
                                    )
                                retryCalls += retryReq
                                retried = true
                                Json.encodeToString(
                                    ApiResponse(
                                        RegistrationInvitationDispatchReceipt(
                                            invitationId = retryReq.invitationId,
                                            delivery = VerificationDeliveryStatus.ACCEPTED,
                                            expiresAtEpochSeconds = now + 86_400,
                                        ),
                                    ),
                                )
                            }
                            "/admin/accounts/invitation-authorization" -> {
                                val authReq =
                                    Json.decodeFromString<AuthorizeRegistrationInvitationRequest>(
                                        (request.body as TextContent).text,
                                    )
                                authCalls += authReq
                                authorized = true
                                Json.encodeToString(
                                    ApiResponse(
                                        candidateSummary.copy(
                                            user =
                                                candidateSummary.user.copy(
                                                    roles = setOf(UserRole.TEACHER),
                                                    institutionId = "UPTLAX",
                                                    authRevision = 6,
                                                ),
                                        ),
                                    ),
                                )
                            }
                            else -> error("Unexpected path: ${request.url.encodedPath}")
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )

        val adminVm = AdminViewModel(AdminRepository(client, store), MfaRepository(client, store), Clock.systemUTC())
        try {
            compose.setContent {
                CompaneroTheme {
                    superAdminHistoryScreen(viewModel = adminVm)
                }
            }

            // 1. Webhook delivery badges distinguish BOUNCED vs DELIVERED
            waitTag("invitation_item_$bouncedInviteId")
            compose.onNodeWithTag("invitation_delivery_$bouncedInviteId").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("invitation_delivery_$acceptedInviteId").performScrollTo().assertIsDisplayed()

            // 2. Retry bounced invitation with MFA step-up -> transitions badge to provider ACCEPTED (pending webhook)
            compose.onNodeWithTag("invitation_retry_$bouncedInviteId").performScrollTo().performClick()
            waitTag("admin_password")
            typeExactly("admin_password", "test-only-cred-123", scroll = false)
            typeExactly("admin_otp", "112233", scroll = false)
            compose.onNodeWithTag("admin_confirm").performClick()

            waitText("Aceptado por proveedor (entrega pendiente)")
            assertEquals(1, retryCalls.size)
            assertEquals(bouncedInviteId, retryCalls.single().invitationId)
            assertEquals(stepUpProof, retryCalls.single().proof)
            assertEquals(StepUpAction.ADMIN_INVITE, stepUps.first().action)

            // 3. Explicit MFA authorization of accepted invitation -> transitions invitation to ACTIVE
            compose.onNodeWithTag("invitation_authorize_$acceptedInviteId").performScrollTo().performClick()
            waitTag("admin_password")
            typeExactly("admin_password", "test-only-cred-123", scroll = false)
            typeExactly("admin_otp", "445566", scroll = false)
            compose.onNodeWithTag("admin_confirm").performClick()

            compose.waitUntil(10_000) { authCalls.size == 1 }
            val authCall = authCalls.single()
            assertEquals(acceptedInviteId, authCall.invitationId)
            assertEquals("TEACHER", authCall.profile)
            assertEquals("UPTLAX", authCall.institutionId)
            assertEquals(5, authCall.expectedAuthRevision)
            assertEquals(
                PrivilegedCommandDigests.invitationAuthorization(acceptedInviteId, authCall),
                stepUps.last().payloadDigest,
            )
            assertTrue(stepUps.any { it.action == StepUpAction.ADMIN_AUTHORITY })
        } finally {
            adminVm.viewModelScope.cancel()
            client.close()
        }
    }
}
