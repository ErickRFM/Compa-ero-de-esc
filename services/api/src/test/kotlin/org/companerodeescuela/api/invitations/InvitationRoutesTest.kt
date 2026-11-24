package org.companerodeescuela.api.invitations

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.auth.RefreshSession
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.security.InMemoryMfaRepository
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.security.Totp
import org.companerodeescuela.api.security.mfaRoutes
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.AcceptRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.MfaStepUpRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRetryRequest
import org.companerodeescuela.shared.contracts.RegistrationInvitationPreparation
import org.companerodeescuela.shared.contracts.RegistrationInvitationSummary
import org.companerodeescuela.shared.contracts.RetryRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.StepUpProof
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InvitationRoutesTest {
    private val now = Instant.now()
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `disabled production invitations return 503 instead of 404`() =
        testApplication {
            val settings =
                TestFixtures.settings().copy(
                    environment = Environment.PRODUCTION,
                    productionInvitationsEnabled = false,
                    jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray(),
                    mfaEncryptionKey = ByteArray(32) { it.toByte() },
                )
            val accounts = InMemoryPlatformAccountRepository()
            val sessions = InMemoryRefreshSessionRepository()
            val invitations = InMemoryInvitationRepository()
            val institutions = InMemoryInstitutionRepository()
            val mfa = MfaService(accounts, InMemoryMfaRepository(), MfaSecretCipher(ByteArray(32) { it.toByte() }), clock)
            val service =
                InvitationDispatchService(
                    accounts = accounts,
                    mfa = mfa,
                    repository = invitations,
                    email = InvitationEmailGateway { _, _, _, _ -> VerificationDeliveryStatus.ACCEPTED },
                    institutions = institutions,
                    clock = clock,
                )

            val identity = MockIdentityProvider()
            application {
                configurePlugins(settings, sessions, PlatformSessionAuthority(accounts, identity))
                routing {
                    invitationRoutes(
                        settings = settings,
                        identity = identity,
                        sessions = sessions,
                        accounts = accounts,
                        service = service,
                        invitations = invitations,
                        clock = clock,
                    )
                }
            }

            val prepare =
                client.post("/admin/invitations/prepare") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
            assertEquals(HttpStatusCode.ServiceUnavailable, prepare.status)
            assertTrue(prepare.bodyAsText().contains("Invitations are disabled in this environment"))

            val list = client.get("/admin/invitations")
            assertEquals(HttpStatusCode.ServiceUnavailable, list.status)
        }

    @Test
    fun `revoke rejects non super admin accounts with 403 and allows verified super admin`() =
        testApplication {
            val settings =
                TestFixtures.settings().copy(
                    environment = Environment.LOCAL,
                    jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray(),
                    mfaEncryptionKey = ByteArray(32) { it.toByte() },
                )
            val accounts = InMemoryPlatformAccountRepository()
            val sessions = InMemoryRefreshSessionRepository()
            val invitations = InMemoryInvitationRepository()
            val institutions = InMemoryInstitutionRepository()
            val identity = MockIdentityProvider()
            val mfa = MfaService(accounts, InMemoryMfaRepository(), MfaSecretCipher(ByteArray(32) { it.toByte() }), clock)
            val service =
                InvitationDispatchService(
                    accounts = accounts,
                    mfa = mfa,
                    repository = invitations,
                    email = InvitationEmailGateway { _, _, _, _ -> VerificationDeliveryStatus.ACCEPTED },
                    institutions = institutions,
                    clock = clock,
                )
            val tokens = AuthTokenService(settings)

            val teacher =
                PlatformAccount(
                    id = "teacher-1",
                    displayName = "QA Teacher",
                    email = "teacher@example.test",
                    passwordHash = "unused-hash",
                    roles = setOf(UserRole.TEACHER),
                    accountStatus = AccountStatus.ACTIVE,
                    emailVerifiedAt = now.minusSeconds(60),
                    institutionId = "inst-1",
                )
            val owner =
                PlatformAccount(
                    id = "owner-1",
                    displayName = "QA Owner",
                    email = "owner@example.test",
                    passwordHash = "unused-hash",
                    roles = setOf(UserRole.SUPER_ADMIN),
                    accountStatus = AccountStatus.ACTIVE,
                    emailVerifiedAt = now.minusSeconds(60),
                    institutionId = "inst-1",
                )
            accounts.create(teacher)
            accounts.create(owner)
            val teacherUser = teacher.toUserSummary()
            val ownerUser = owner.toUserSummary()
            sessions.create(
                RefreshSession(
                    id = "session-teacher-1",
                    tokenHash = "hash-1",
                    user = teacherUser,
                    createdAt = now,
                    expiresAt = now.plusSeconds(3600),
                    identitySource = SessionIdentitySource.NATIVE,
                ),
            )
            sessions.create(
                RefreshSession(
                    id = "session-owner-1",
                    tokenHash = "hash-2",
                    user = ownerUser,
                    createdAt = now,
                    expiresAt = now.plusSeconds(3600),
                    identitySource = SessionIdentitySource.NATIVE,
                ),
            )
            assertTrue(
                invitations.create(
                    InvitationRecord(
                        id = "inv-1",
                        recipient = "new.teacher@example.test",
                        institutionId = "inst-1",
                        profile = InvitationProfile.TEACHER,
                        issuerId = "owner-1",
                        createdAt = now,
                        expiresAt = now.plusSeconds(3600),
                        status = InvitationStatus.SENT,
                        tokenHash = InvitationToken.generate().hash,
                    ),
                ),
            )

            application {
                configurePlugins(settings, sessions, PlatformSessionAuthority(accounts, identity))
                routing {
                    invitationRoutes(
                        settings = settings,
                        identity = identity,
                        sessions = sessions,
                        accounts = accounts,
                        service = service,
                        invitations = invitations,
                        clock = clock,
                    )
                }
            }

            val teacherToken = tokens.issue(teacherUser, "session-teacher-1").value
            val forbidden =
                client.post("/admin/invitations/revoke") {
                    bearerAuth(teacherToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"invitationId":"inv-1"}""")
                }
            assertEquals(HttpStatusCode.Forbidden, forbidden.status)
            assertEquals(InvitationStatus.SENT, invitations.find("inv-1")?.status)

            val ownerToken = tokens.issue(ownerUser, "session-owner-1").value
            val allowed =
                client.post("/admin/invitations/revoke") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"invitationId":"inv-1"}""")
                }
            assertEquals(HttpStatusCode.OK, allowed.status)
            assertEquals(InvitationStatus.REVOKED, invitations.find("inv-1")?.status)
        }

    @Test
    fun `reproducible retry prepare survives clock advance, rotates token, prevents replay, and exposes delivery statuses`() =
        testApplication {
            val json = Json { ignoreUnknownKeys = true }
            val mutableClock =
                object : Clock() {
                    var current: Instant = Instant.now()

                    override fun instant(): Instant = current

                    override fun getZone(): ZoneId = ZoneOffset.UTC

                    override fun withZone(zone: ZoneId): Clock = this
                }
            val password = "owner-strong-password-123"
            val settings =
                TestFixtures.settings().copy(
                    environment = Environment.LOCAL,
                    jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray(),
                    mfaEncryptionKey = ByteArray(32) { it.toByte() },
                )
            val accounts = InMemoryPlatformAccountRepository()
            val sessions = InMemoryRefreshSessionRepository()
            val invitations = InMemoryInvitationRepository()
            val institutions = InMemoryInstitutionRepository(listOf(InstitutionSummary("UPTLAX", "UPTLAX")))
            val identity = MockIdentityProvider()
            val mfa = MfaService(accounts, InMemoryMfaRepository(), MfaSecretCipher(ByteArray(32) { it.toByte() }), mutableClock)
            val sentTokens = mutableListOf<String>()
            var nextDeliveryStatus = VerificationDeliveryStatus.UNCONFIRMED
            val service =
                InvitationDispatchService(
                    accounts = accounts,
                    mfa = mfa,
                    repository = invitations,
                    email =
                        InvitationEmailGateway { _, code, _, _ ->
                            sentTokens += code
                            nextDeliveryStatus
                        },
                    institutions = institutions,
                    clock = mutableClock,
                )
            val tokens = AuthTokenService(settings)

            val owner =
                PlatformAccount(
                    id = "owner-1",
                    displayName = "QA Owner",
                    email = "owner@example.test",
                    passwordHash = PasswordHasher().hash(password),
                    roles = setOf(UserRole.SUPER_ADMIN),
                    accountStatus = AccountStatus.ACTIVE,
                    emailVerifiedAt = mutableClock.current.minusSeconds(120),
                    institutionId = "UPTLAX",
                )
            val recipient =
                PlatformAccount(
                    id = "docente-1",
                    displayName = "Docente Invitado",
                    email = "docente.invitado@example.test",
                    passwordHash = PasswordHasher().hash("docente-password-123"),
                    roles = setOf(UserRole.STUDENT),
                    accountStatus = AccountStatus.ACTIVE,
                    emailVerifiedAt = mutableClock.current.minusSeconds(60),
                )
            accounts.create(owner)
            accounts.create(recipient)

            val enrollment = mfa.beginEnrollment(owner, password)
            val enrollOtp = Totp.code(Totp.decodeSecret(enrollment.secret), mutableClock.current.epochSecond)
            mfa.confirmEnrollment(owner, enrollment.enrollmentId, enrollOtp)

            val ownerUser = owner.toUserSummary()
            val recipientUser = recipient.toUserSummary()
            sessions.create(
                RefreshSession(
                    id = "session-owner",
                    tokenHash = "h-owner",
                    user = ownerUser,
                    createdAt = mutableClock.current,
                    expiresAt = mutableClock.current.plusSeconds(3600),
                    identitySource = SessionIdentitySource.NATIVE,
                ),
            )
            sessions.create(
                RefreshSession(
                    id = "session-recipient",
                    tokenHash = "h-recipient",
                    user = recipientUser,
                    createdAt = mutableClock.current,
                    expiresAt = mutableClock.current.plusSeconds(3600),
                    identitySource = SessionIdentitySource.NATIVE,
                ),
            )

            application {
                configurePlugins(settings, sessions, PlatformSessionAuthority(accounts, identity))
                routing {
                    mfaRoutes(settings, identity, sessions, accounts, mfa)
                    invitationRoutes(
                        settings = settings,
                        identity = identity,
                        sessions = sessions,
                        accounts = accounts,
                        service = service,
                        invitations = invitations,
                        clock = mutableClock,
                    )
                }
            }

            val ownerToken = tokens.issue(ownerUser, "session-owner").value
            val recipientToken = tokens.issue(recipientUser, "session-recipient").value

            // 1. Prepare and dispatch initial TEACHER invitation (returns UNCONFIRMED)
            val sendOpId = "send-op-12345678"
            val sendReq = PrepareRegistrationInvitationRequest("docente.invitado@example.test", "UPTLAX", "TEACHER", sendOpId)
            val prepResp =
                client.post("/admin/invitations/prepare") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(sendReq))
                }
            assertEquals(HttpStatusCode.OK, prepResp.status)
            val sendPrep = json.decodeFromString<ApiResponse<RegistrationInvitationPreparation>>(prepResp.bodyAsText()).data

            mutableClock.current = mutableClock.current.plusSeconds(35)
            val sendOtp = Totp.code(Totp.decodeSecret(enrollment.secret), mutableClock.current.epochSecond)
            val sendProofResp =
                client.post("/auth/mfa/step-up") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            MfaStepUpRequest(
                                password = password,
                                otp = sendOtp,
                                action = StepUpAction.ADMIN_INVITE,
                                resourceId = sendPrep.resourceId,
                                operationId = sendPrep.operationId,
                                payloadDigest = sendPrep.payloadDigest,
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, sendProofResp.status)
            val sendProof = json.decodeFromString<ApiResponse<StepUpProof>>(sendProofResp.bodyAsText()).data.proof

            val dispatchResp =
                client.post("/admin/invitations") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(sendReq.copy(proof = sendProof)))
                }
            assertEquals(HttpStatusCode.Created, dispatchResp.status)
            assertEquals(1, sentTokens.size)
            val firstCode = sentTokens.first()

            val initialList =
                json
                    .decodeFromString<ApiResponse<List<RegistrationInvitationSummary>>>(
                        client
                            .get("/admin/invitations") { bearerAuth(ownerToken) }
                            .bodyAsText(),
                    ).data
            val invitationId = initialList.single().invitationId
            assertEquals("UNCONFIRMED", initialList.single().deliveryStatus)

            // 2. Prepare retry, then advance server clock by 45 seconds before executing retry!
            nextDeliveryStatus = VerificationDeliveryStatus.ACCEPTED
            val retryOpId = "retry-op-87654321"
            val retryPrepResp =
                client.post("/admin/invitations/retry/prepare") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(PrepareRegistrationInvitationRetryRequest(invitationId, retryOpId)))
                }
            assertEquals(HttpStatusCode.OK, retryPrepResp.status)
            val retryPrep = json.decodeFromString<ApiResponse<RegistrationInvitationPreparation>>(retryPrepResp.bodyAsText()).data
            assertEquals(retryOpId, retryPrep.operationId)

            mutableClock.current = mutableClock.current.plusSeconds(45)
            val retryOtp = Totp.code(Totp.decodeSecret(enrollment.secret), mutableClock.current.epochSecond)
            val retryProofResp =
                client.post("/auth/mfa/step-up") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            MfaStepUpRequest(
                                password = password,
                                otp = retryOtp,
                                action = StepUpAction.ADMIN_INVITE,
                                resourceId = retryPrep.resourceId,
                                operationId = retryPrep.operationId,
                                payloadDigest = retryPrep.payloadDigest,
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, retryProofResp.status)
            val retryProof = json.decodeFromString<ApiResponse<StepUpProof>>(retryProofResp.bodyAsText()).data.proof

            // Advance another 12 seconds: server time differs from both prepare and step-up, yet retry succeeds!
            mutableClock.current = mutableClock.current.plusSeconds(12)
            val retryExecResp =
                client.post("/admin/invitations/retry") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(RetryRegistrationInvitationRequest(invitationId, retryOpId, retryProof)))
                }
            assertEquals(HttpStatusCode.OK, retryExecResp.status)
            assertEquals(2, sentTokens.size)
            val secondCode = sentTokens.last()
            assertNotEquals(firstCode, secondCode)

            // 3. Anti-replay: replaying same proof fails (403), and replaying same retryOpId with a fresh proof fails (409)
            val replaySameProof =
                client.post("/admin/invitations/retry") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(RetryRegistrationInvitationRequest(invitationId, retryOpId, retryProof)))
                }
            assertEquals(HttpStatusCode.Conflict, replaySameProof.status)

            val replayPrepare =
                client.post("/admin/invitations/retry/prepare") {
                    bearerAuth(ownerToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(PrepareRegistrationInvitationRetryRequest(invitationId, retryOpId)))
                }
            assertEquals(HttpStatusCode.Conflict, replayPrepare.status)

            // 4. Webhook-confirmed delivery statuses (ACCEPTED -> BOUNCED -> DELIVERED) are exposed truthfully
            val afterRetryList =
                json
                    .decodeFromString<ApiResponse<List<RegistrationInvitationSummary>>>(
                        client.get("/admin/invitations") { bearerAuth(ownerToken) }.bodyAsText(),
                    ).data
            assertEquals("ACCEPTED", afterRetryList.single().deliveryStatus)

            invitations.updateDeliveryStatus(invitationId, "BOUNCED", mutableClock.current.plusSeconds(5))
            val bouncedList =
                json
                    .decodeFromString<ApiResponse<List<RegistrationInvitationSummary>>>(
                        client.get("/admin/invitations") { bearerAuth(ownerToken) }.bodyAsText(),
                    ).data
            assertEquals("BOUNCED", bouncedList.single().deliveryStatus)

            invitations.updateDeliveryStatus(invitationId, "DELIVERED", mutableClock.current.plusSeconds(10))
            val deliveredList =
                json
                    .decodeFromString<ApiResponse<List<RegistrationInvitationSummary>>>(
                        client.get("/admin/invitations") { bearerAuth(ownerToken) }.bodyAsText(),
                    ).data
            assertEquals("DELIVERED", deliveredList.single().deliveryStatus)

            // 5. Old code from initial dispatch is rejected; rotated code succeeds without granting TEACHER yet
            val staleAccept =
                client.post("/auth/invitations/accept") {
                    bearerAuth(recipientToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(AcceptRegistrationInvitationRequest(invitationId, firstCode, "accept-stale-123")))
                }
            assertEquals(HttpStatusCode.BadRequest, staleAccept.status)

            val validAccept =
                client.post("/auth/invitations/accept") {
                    bearerAuth(recipientToken)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(AcceptRegistrationInvitationRequest(invitationId, secondCode, "accept-valid-123")))
                }
            assertEquals(HttpStatusCode.OK, validAccept.status)
            assertEquals(setOf(UserRole.STUDENT), accounts.findById(recipient.id)?.roles)
        }
}
