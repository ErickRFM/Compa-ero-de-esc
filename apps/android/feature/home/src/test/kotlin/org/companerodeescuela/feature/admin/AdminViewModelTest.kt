@file:Suppress("ktlint:standard:no-wildcard-imports")

package org.companerodeescuela.feature.admin

import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.common.result.*
import org.companerodeescuela.core.network.*
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {
    private suspend fun <T> awaitReal(block: suspend () -> T): T = withContext(Dispatchers.Default) { withTimeout(5000) { block() } }

    private class Store : SessionTokenStore {
        val token = MutableStateFlow<String?>(jwt("admin"))

        override suspend fun readAccessToken() = token.value

        override suspend fun writeAccessToken(token: String) {
            this.token.value = token
        }

        override suspend fun clear() {
            token.value = null
        }

        override fun observeAccessToken(): Flow<String?> = token
    }

    private class Fixture {
        val store = Store()
        val clock = Clock.fixed(Instant.ofEpochSecond(1900000000), ZoneOffset.UTC)
        val application = StaffApplication("application", StaffRole.TUTOR, "campus", "QA-ID", requestedAtEpochSeconds = 1)
        val target =
            AdminUserSummary(
                UserSummary(
                    "target",
                    "Actual Teacher",
                    roles = setOf(UserRole.TEACHER),
                    institutionId = "campus",
                    emailVerified = true,
                    authRevision = 7,
                ),
                listOf(application),
            )
        val overview = AdministrationOverview(listOf(target), AdministrationMetrics(1, 1, 1, 0, 0))
        val group =
            AcademicGroupSummary(
                "group",
                "Actual Group",
                institutionId = "campus",
                periodId = "period",
                startsAtEpochSeconds = 1800000000,
                endsAtEpochSeconds = 2000000000,
                scopeRevision = 4,
            )
        var overviewStatus = HttpStatusCode.OK
        var firstApprovalStatus = HttpStatusCode.OK
        var gate: CompletableDeferred<Unit>? = null
        val proofStarted = CompletableDeferred<Unit>()
        val proofs = mutableListOf<MfaStepUpRequest>()
        val approvals = mutableListOf<ApproveStaffRequest>()
        val lifecycle = mutableListOf<AccountLifecycleRequest>()
        val revocations = mutableListOf<RevokeTutorAssignmentRequest>()
        val assignment =
            TutorAssignmentSummary(
                "assignment",
                "target",
                "group",
                "Actual Group",
                true,
                "admin",
                1,
                institutionId = "campus",
                periodId = "period",
                startsAtEpochSeconds = 1800000000,
                endsAtEpochSeconds = 2000000000,
                groupScopeRevision = 4,
            )
        val client =
            createApiClient(
                ApiEnvironment("https://example.test/", "test"),
                MockEngine { request ->
                    var status = HttpStatusCode.OK
                    val body =
                        when (request.url.encodedPath) {
                            "/admin/accounts" -> {
                                status = overviewStatus
                                Json.encodeToString(ApiResponse(overview))
                            }
                            "/admin/accounts/approval-groups" -> Json.encodeToString(ApiResponse(listOf(group)))
                            "/tutoring/assignments" -> Json.encodeToString(ApiResponse(listOf(assignment)))
                            "/auth/mfa/step-up" -> {
                                proofs += Json.decodeFromString<MfaStepUpRequest>((request.body as TextContent).text)
                                proofStarted.complete(Unit)
                                gate?.let { withContext(NonCancellable) { it.await() } }
                                Json.encodeToString(ApiResponse(StepUpProof("TEST-PROOF", 1900000200)))
                            }
                            "/admin/accounts/target/staff-approval" -> {
                                approvals += Json.decodeFromString<ApproveStaffRequest>((request.body as TextContent).text)
                                if (approvals.size == 1) status = firstApprovalStatus
                                Json.encodeToString(
                                    ApiResponse(
                                        target.copy(applications = listOf(application.copy(status = StaffApplicationStatus.APPROVED))),
                                    ),
                                )
                            }
                            "/admin/accounts/target/lifecycle" -> {
                                lifecycle +=
                                    Json.decodeFromString<AccountLifecycleRequest>((request.body as TextContent).text)
                                Json.encodeToString(ApiResponse(target))
                            }
                            "/tutoring/assignments/assignment/revoke" -> {
                                revocations +=
                                    Json.decodeFromString<RevokeTutorAssignmentRequest>((request.body as TextContent).text)
                                Json.encodeToString(ApiResponse(assignment.copy(active = false)))
                            }
                            else -> error("Unexpected endpoint ${request.url.encodedPath}")
                        }
                    respond(if (status == HttpStatusCode.OK) body else "{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        val vm = AdminViewModel(AdminRepository(client, store), MfaRepository(client, store), clock)

        suspend fun ready() {
            awaitState { !it.loading }
            assertEquals(overview, vm.state.value.overview)
        }

        suspend fun awaitState(predicate: (AdminUiState) -> Boolean) =
            withContext(Dispatchers.Default) {
                withTimeout(5000) { vm.state.first(predicate) }
            }

        suspend fun close() {
            gate?.complete(Unit)
            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            client.close()
        }
    }

    @Test fun `actual approval binds selected current group revision and digest to MFA and refreshes real overview`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                f.vm.prepareApproval("target", "application", setOf("group"))
                assertNotNull(f.vm.state.value.operation)
                f.vm.authorize("test-password", "123456")
                f.awaitState { !it.working && it.completedOperationId != null }
                val command = f.approvals.single()
                val proof = f.proofs.single()
                assertEquals(7, command.expectedAuthRevision)
                assertEquals("campus", command.institutionId)
                assertEquals(listOf(TutorApprovalScope("group", "period", 1900000000, 2000000000, 4)), command.tutorScopes)
                assertEquals(command.operationId, proof.operationId)
                assertEquals("target", proof.resourceId)
                assertEquals(PrivilegedCommandDigests.staffApproval("target", command), proof.payloadDigest)
                assertEquals(StepUpAction.APPROVE_STAFF, proof.action)
                assertEquals("TEST-PROOF", command.proof)
                assertFalse(
                    f.vm.state.value
                        .toString()
                        .contains("TEST-PROOF"),
                )
                assertFalse(
                    f.vm.state.value
                        .toString()
                        .contains("test-password"),
                )
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `ambiguous approval retry retains exact operation payload and unexpired proof`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            f.firstApprovalStatus = HttpStatusCode.ServiceUnavailable
            try {
                f.ready()
                f.vm.prepareApproval("target", "application", setOf("group"))
                f.vm.authorize("test-password", "123456")
                f.awaitState { !it.working && it.failure != null }
                assertNotNull(f.vm.state.value.operation)
                f.vm.authorize("", " ")
                f.awaitState { !it.working && it.completedOperationId != null }
                assertEquals(2, f.approvals.size)
                assertEquals(f.approvals[0], f.approvals[1])
                assertEquals(1, f.proofs.size)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `late MFA proof from previous account never executes a command for replacement owner`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            f.gate = CompletableDeferred()
            try {
                f.ready()
                f.vm.prepareApproval("target", "application", setOf("group"))
                f.vm.authorize("test-password", "123456")
                awaitReal { f.proofStarted.await() }
                f.store.token.value = jwt("replacement")
                runCurrent()
                f.gate!!.complete(Unit)
                f.awaitState { !it.loading && !it.working }
                assertNull(f.vm.state.value.operation)
                assertTrue(f.approvals.isEmpty())
                assertNull(f.vm.state.value.completedOperationId)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `scope denial clears loaded people groups assignments and pending command instead of fake metrics`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                f.vm.prepareApproval("target", "application", setOf("group"))
                f.overviewStatus = HttpStatusCode.Forbidden
                f.vm.refresh()
                f.awaitState { !it.loading }
                assertNull(f.vm.state.value.overview)
                assertTrue(
                    f.vm.state.value.groups
                        .isEmpty(),
                )
                assertTrue(
                    f.vm.state.value.assignments
                        .isEmpty(),
                )
                assertNull(f.vm.state.value.operation)
                assertEquals(403, assertIs<AppError.Http>(f.vm.state.value.failure).status)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `suspension and tutor revocation use distinct exact backend actions and never mutate local authority`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                val original = f.store.token.value
                f.vm.prepareLifecycle("target", AccountStatus.SUSPENDED)
                f.vm.authorize("test-password", "123456")
                f.awaitState { !it.loading && !it.working && it.completedOperationId != null }
                assertEquals(StepUpAction.ACCOUNT_LIFECYCLE, f.proofs[0].action)
                assertEquals(PrivilegedCommandDigests.accountLifecycle("target", f.lifecycle.single()), f.proofs[0].payloadDigest)
                f.vm.prepareRevocation("assignment")
                assertNotNull(f.vm.state.value.operation)
                f.vm.authorize("test-password", "234567")
                f.awaitState { !it.working }
                assertNull(f.vm.state.value.failure)
                assertEquals(f.revocations.single().operationId, f.vm.state.value.completedOperationId)
                assertEquals(StepUpAction.MANAGE_ACADEMICS, f.proofs[1].action)
                assertEquals(PrivilegedCommandDigests.tutorRevocation("assignment", f.revocations.single()), f.proofs[1].payloadDigest)
                assertEquals(original, f.store.token.value)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `operator selected tutor validity remains bounded by actual group period`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                f.vm.prepareApprovalWithValidity("target", "application", setOf("group"), 2)
                f.vm.authorize("test-password", "123456")
                f.awaitState { !it.loading && !it.working && it.completedOperationId != null }
                val request = f.approvals.single()
                assertEquals(1900172800, request.tutorScopes.single().endsAtEpochSeconds)
                assertEquals(PrivilegedCommandDigests.staffApproval("target", request), f.proofs.single().payloadDigest)
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    @Test fun `invalid tutor duration cannot prepare a grant or obtain MFA evidence`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val f = Fixture()
            try {
                f.ready()
                f.vm.prepareApprovalWithValidity("target", "application", setOf("group"), 0)
                assertNull(f.vm.state.value.operation)
                assertEquals(422, assertIs<AppError.Http>(f.vm.state.value.failure).status)
                assertTrue(f.proofs.isEmpty())
            } finally {
                f.close()
                Dispatchers.resetMain()
            }
        }

    companion object {
        private fun jwt(id: String) =
            "e30." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    """{"sub":"$id","session_id":"session-$id","roles":["ADMIN"],"exp":4102444800}""".toByteArray(),
                ) +
                ".test"
    }
}
