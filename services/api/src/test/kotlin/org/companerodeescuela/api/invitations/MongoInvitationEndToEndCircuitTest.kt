package org.companerodeescuela.api.invitations

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bson.Document
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.admin.AccountAdministrationService
import org.companerodeescuela.api.admin.accountAdministrationRoutes
import org.companerodeescuela.api.audit.MongoPlatformAuditLog
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.MongoPlatformAccountRepository
import org.companerodeescuela.api.auth.MongoRateLimitStore
import org.companerodeescuela.api.auth.MongoRefreshSessionRepository
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.auth.RefreshSession
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.mail.ResendInvitationEmailGateway
import org.companerodeescuela.api.mail.resendWebhookRoutes
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.security.MfaSecretCipher
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.security.MongoMfaRepository
import org.companerodeescuela.api.security.Totp
import org.companerodeescuela.api.security.mfaRoutes
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.api.tutoring.InMemoryTutorAssignmentRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentService
import org.companerodeescuela.shared.contracts.AcceptRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.AdminAuditEntry
import org.companerodeescuela.shared.contracts.AdminUserSummary
import org.companerodeescuela.shared.contracts.ApiError
import org.companerodeescuela.shared.contracts.ApiErrorCode
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AuthorizeRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.InstitutionSummary
import org.companerodeescuela.shared.contracts.MfaStepUpRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.PrepareRegistrationInvitationRetryRequest
import org.companerodeescuela.shared.contracts.PrivilegedCommandDigests
import org.companerodeescuela.shared.contracts.RegistrationInvitationPreparation
import org.companerodeescuela.shared.contracts.RegistrationInvitationSummary
import org.companerodeescuela.shared.contracts.RetryRegistrationInvitationRequest
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.shared.contracts.StepUpProof
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.InetSocketAddress
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "V12_TEST_MONGODB_URI", matches = ".+")
@Timeout(120)
class MongoInvitationEndToEndCircuitTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val ownerCred = "owner-mongo-secret-123"

    private class MutableClock : Clock() {
        var current: Instant = Instant.now().minusSeconds(120)

        override fun instant(): Instant = current

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private data class CapturedMail(
        val idempotencyKey: String?,
        val body: String,
    )

    private inner class MongoHarness(
        val db: MongoDatabase,
        mailPort: Int,
    ) {
        val clock = MutableClock()
        val webhookKeyBytes = ByteArray(32) { (it + 7).toByte() }
        val webhookSecret = "whsec_" + Base64.getEncoder().encodeToString(webhookKeyBytes)
        val mfaBytes = ByteArray(32) { (it + 3).toByte() }
        val settings =
            TestFixtures.settings().copy(
                environment = Environment.LOCAL,
                jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray(),
                mfaEncryptionKey = mfaBytes,
            )
        val accounts = MongoPlatformAccountRepository(db)
        val sessions = MongoRefreshSessionRepository(db)
        val invitations = MongoInvitationRepository(db)
        val auditLog = MongoPlatformAuditLog(db)
        val rateLimits = MongoRateLimitStore(db)
        val institutions = InMemoryInstitutionRepository(listOf(InstitutionSummary("UPTLAX", "UPTLAX")))
        val groups = InMemoryAcademicGroupRepository()
        val identity = MockIdentityProvider()
        val mfa = MfaService(accounts, MongoMfaRepository(db), MfaSecretCipher(mfaBytes), clock)
        val resendGateway =
            ResendInvitationEmailGateway(
                apiKey = "re_test_loopback_only".toCharArray(),
                sender = "invitaciones@example.test",
                endpoint = URI("http://127.0.0.1:$mailPort/emails"),
            )
        val dispatchService = InvitationDispatchService(accounts, mfa, invitations, resendGateway, institutions, clock = clock)
        val tutoring = TutorAssignmentService(InMemoryTutorAssignmentRepository(), groups, clock, accounts)
        val adminService = AccountAdministrationService(accounts, institutions, groups, tutoring, mfa, clock, invitations)
        val authTokens = AuthTokenService(settings)

        fun mount(builder: ApplicationTestBuilder) {
            builder.application {
                configurePlugins(settings, sessions, PlatformSessionAuthority(accounts, identity))
                routing {
                    mfaRoutes(settings, identity, sessions, accounts, mfa)
                    invitationRoutes(
                        settings = settings,
                        identity = identity,
                        sessions = sessions,
                        accounts = accounts,
                        service = dispatchService,
                        invitations = invitations,
                        clock = clock,
                        rateLimitStore = rateLimits,
                        auditLog = auditLog,
                    )
                    resendWebhookRoutes(webhookSecret.toCharArray(), db)
                    accountAdministrationRoutes(settings, identity, sessions, accounts, adminService, auditLog)
                }
            }
        }

        suspend fun createBearer(
            id: String,
            email: String,
            roles: Set<UserRole>,
            institutionId: String? = null,
        ): Pair<PlatformAccount, String> {
            val account =
                PlatformAccount(
                    id = id,
                    displayName = id,
                    email = email,
                    passwordHash = PasswordHasher().hash(ownerCred),
                    roles = roles,
                    accountStatus = AccountStatus.ACTIVE,
                    emailVerifiedAt = clock.current.minusSeconds(180),
                    institutionId = institutionId,
                )
            assertTrue(accounts.create(account))
            val summary = account.toUserSummary()
            val sessionId = "sess-$id"
            sessions.create(
                RefreshSession(
                    id = sessionId,
                    tokenHash = "hash-$id",
                    user = summary,
                    createdAt = clock.current,
                    expiresAt = clock.current.plusSeconds(3600),
                    identitySource = SessionIdentitySource.NATIVE,
                ),
            )
            return account to authTokens.issue(summary, sessionId).value
        }

        suspend fun issueProof(
            client: HttpClient,
            bearer: String,
            secret: ByteArray,
            action: StepUpAction,
            resourceId: String,
            operationId: String,
            digest: String,
        ): String {
            val resp =
                client.post("/auth/mfa/step-up") {
                    bearerAuth(bearer)
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            MfaStepUpRequest(
                                ownerCred,
                                Totp.code(secret, clock.current.epochSecond),
                                action,
                                resourceId,
                                operationId,
                                digest,
                            ),
                        ),
                    )
                }
            assertEquals(HttpStatusCode.OK, resp.status)
            return json.decodeFromString<ApiResponse<StepUpProof>>(resp.bodyAsText()).data.proof
        }

        fun signSvix(
            msgId: String,
            tsSeconds: Long,
            body: String,
        ): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(webhookKeyBytes, "HmacSHA256"))
            val signed = "$msgId.$tsSeconds.$body".toByteArray(Charsets.UTF_8)
            return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(signed))
        }
    }

    @Test
    fun `real Mongo enforces MFA unconfigured, wrong password, invalid OTP, replayed OTP, and expired proof`() =
        testApplication {
            val uri = requireNotNull(System.getenv("V12_TEST_MONGODB_URI"))
            require(uri == "mongodb://127.0.0.1:27017")
            val mongoClient = MongoClient.create(uri)
            val db = mongoClient.getDatabase("invite_mfa_" + UUID.randomUUID().toString().replace("-", ""))
            try {
                val h = MongoHarness(db, mailPort = 9)
                val (owner, bearer) = h.createBearer("owner-mfa", "owner.mfa@example.test", setOf(UserRole.SUPER_ADMIN), "UPTLAX")
                h.mount(this)
                val dummyDigest = "a".repeat(64)

                val unconfigured =
                    client.post("/auth/mfa/step-up") {
                        bearerAuth(bearer)
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                MfaStepUpRequest(ownerCred, "123456", StepUpAction.ADMIN_INVITE, "UPTLAX", "neg-op-12345678", dummyDigest),
                            ),
                        )
                    }
                assertEquals(HttpStatusCode.Forbidden, unconfigured.status)
                assertEquals(ApiErrorCode.MFA_ENROLLMENT_REQUIRED, json.decodeFromString<ApiError>(unconfigured.bodyAsText()).code)

                val enroll = h.mfa.beginEnrollment(owner, ownerCred)
                val secret = Totp.decodeSecret(enroll.secret)
                h.mfa.confirmEnrollment(owner, enroll.enrollmentId, Totp.code(secret, h.clock.current.epochSecond))
                h.clock.current = h.clock.current.plusSeconds(31)

                val wrongPass =
                    client.post("/auth/mfa/step-up") {
                        bearerAuth(bearer)
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                MfaStepUpRequest(
                                    "wrong-secret-999",
                                    Totp.code(secret, h.clock.current.epochSecond),
                                    StepUpAction.ADMIN_INVITE,
                                    "UPTLAX",
                                    "neg-op-12345678",
                                    dummyDigest,
                                ),
                            ),
                        )
                    }
                assertEquals(HttpStatusCode.Forbidden, wrongPass.status)
                assertEquals(ApiErrorCode.MFA_REAUTHENTICATION_FAILED, json.decodeFromString<ApiError>(wrongPass.bodyAsText()).code)

                val badOtp =
                    client.post("/auth/mfa/step-up") {
                        bearerAuth(bearer)
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                MfaStepUpRequest(ownerCred, "000000", StepUpAction.ADMIN_INVITE, "UPTLAX", "neg-op-12345678", dummyDigest),
                            ),
                        )
                    }
                assertEquals(HttpStatusCode.Forbidden, badOtp.status)
                assertEquals(ApiErrorCode.MFA_CODE_INVALID, json.decodeFromString<ApiError>(badOtp.bodyAsText()).code)

                val prepResp =
                    client.post("/admin/invitations/prepare") {
                        bearerAuth(bearer)
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                PrepareRegistrationInvitationRequest("cand.neg@example.test", "UPTLAX", "TEACHER", "neg-exp-12345678"),
                            ),
                        )
                    }
                val prep = json.decodeFromString<ApiResponse<RegistrationInvitationPreparation>>(prepResp.bodyAsText()).data
                val validOtp = Totp.code(secret, h.clock.current.epochSecond)
                val expiringProof =
                    h.issueProof(
                        client,
                        bearer,
                        secret,
                        StepUpAction.ADMIN_INVITE,
                        prep.resourceId,
                        prep.operationId,
                        prep.payloadDigest,
                    )

                val replayed =
                    client.post("/auth/mfa/step-up") {
                        bearerAuth(bearer)
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                MfaStepUpRequest(
                                    ownerCred,
                                    validOtp,
                                    StepUpAction.ADMIN_INVITE,
                                    prep.resourceId,
                                    prep.operationId,
                                    prep.payloadDigest,
                                ),
                            ),
                        )
                    }
                assertEquals(HttpStatusCode.Forbidden, replayed.status)
                assertEquals(ApiErrorCode.MFA_CODE_REUSED, json.decodeFromString<ApiError>(replayed.bodyAsText()).code)

                h.clock.current = h.clock.current.plusSeconds(305)
                val expiredDispatch =
                    client.post("/admin/invitations") {
                        bearerAuth(bearer)
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                PrepareRegistrationInvitationRequest(
                                    "cand.neg@example.test",
                                    "UPTLAX",
                                    "TEACHER",
                                    "neg-exp-12345678",
                                    expiringProof,
                                ),
                            ),
                        )
                    }
                assertEquals(HttpStatusCode.Forbidden, expiredDispatch.status)
                assertEquals(ApiErrorCode.FORBIDDEN, json.decodeFromString<ApiError>(expiredDispatch.bodyAsText()).code)
            } finally {
                runBlocking { db.drop() }
                mongoClient.close()
            }
        }

    @Test
    fun `real Mongo verifies Resend controlled mailbox, signed Svix webhooks, retry, acceptance, authorization, and indexes`() =
        testApplication {
            val uri = requireNotNull(System.getenv("V12_TEST_MONGODB_URI"))
            require(uri == "mongodb://127.0.0.1:27017")
            val capturedEmails = CopyOnWriteArrayList<CapturedMail>()
            val mailServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            mailServer.createContext("/emails") { exchange ->
                val payload = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                capturedEmails += CapturedMail(exchange.requestHeaders.getFirst("Idempotency-Key"), payload)
                val responseBytes = """{"id":"resend-msg-${capturedEmails.size}"}""".toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                exchange.responseBody.use { it.write(responseBytes) }
            }
            mailServer.start()

            val mongoClient = MongoClient.create(uri)
            val db = mongoClient.getDatabase("invite_e2e_" + UUID.randomUUID().toString().replace("-", ""))
            try {
                val h = MongoHarness(db, mailServer.address.port)
                val (owner, ownerBearer) = h.createBearer("owner-1", "owner.e2e@example.test", setOf(UserRole.SUPER_ADMIN), "UPTLAX")
                val (invited, teacherBearer) = h.createBearer("docente-1", "docente.e2e@example.test", setOf(UserRole.STUDENT))
                val enroll = h.mfa.beginEnrollment(owner, ownerCred)
                val ownerSecret = Totp.decodeSecret(enroll.secret)
                h.mfa.confirmEnrollment(owner, enroll.enrollmentId, Totp.code(ownerSecret, h.clock.current.epochSecond))
                h.clock.current = h.clock.current.plusSeconds(31)
                h.mount(this)

                val (invitationId, firstCode) = dispatchInitialInvitation(h, ownerBearer, ownerSecret, capturedEmails)
                verifySignedWebhookTransitions(h, ownerBearer, invitationId)
                val secondCode = retryAndConfirmDelivery(h, ownerBearer, ownerSecret, invitationId, firstCode, capturedEmails)
                acceptAndAuthorizeTeacher(h, ownerBearer, ownerSecret, invited.id, teacherBearer, invitationId, secondCode)
                assertMongoIndexes(db)
            } finally {
                mailServer.stop(0)
                runBlocking { db.drop() }
                mongoClient.close()
            }
        }

    private suspend fun ApplicationTestBuilder.dispatchInitialInvitation(
        h: MongoHarness,
        ownerBearer: String,
        ownerSecret: ByteArray,
        capturedEmails: List<CapturedMail>,
    ): Pair<String, String> {
        val sendReq = PrepareRegistrationInvitationRequest("docente.e2e@example.test", "UPTLAX", "TEACHER", "e2e-send-12345678")
        val prepResp =
            client.post("/admin/invitations/prepare") {
                bearerAuth(ownerBearer)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(sendReq))
            }
        val prep = json.decodeFromString<ApiResponse<RegistrationInvitationPreparation>>(prepResp.bodyAsText()).data
        val sendProof =
            h.issueProof(
                client,
                ownerBearer,
                ownerSecret,
                StepUpAction.ADMIN_INVITE,
                prep.resourceId,
                prep.operationId,
                prep.payloadDigest,
            )
        val dispatchResp =
            client.post("/admin/invitations") {
                bearerAuth(ownerBearer)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(sendReq.copy(proof = sendProof)))
            }
        assertEquals(HttpStatusCode.Created, dispatchResp.status)
        assertEquals(1, capturedEmails.size)

        val firstMail = Json.parseToJsonElement(capturedEmails[0].body).jsonObject
        val invitationId =
            firstMail["tags"]!!
                .jsonArray
                .first { it.jsonObject["name"]!!.jsonPrimitive.content == "invitation_id" }
                .jsonObject["value"]!!
                .jsonPrimitive.content
        val firstCode =
            Regex("Código de invitación: ([A-Za-z0-9_-]{43})")
                .find(firstMail["text"]!!.jsonPrimitive.content)!!
                .groupValues[1]
        val listResp = client.get("/admin/invitations") { bearerAuth(ownerBearer) }
        val summary = json.decodeFromString<ApiResponse<List<RegistrationInvitationSummary>>>(listResp.bodyAsText()).data.single()
        assertEquals("ACCEPTED", summary.deliveryStatus)
        return invitationId to firstCode
    }

    private suspend fun ApplicationTestBuilder.verifySignedWebhookTransitions(
        h: MongoHarness,
        ownerBearer: String,
        invitationId: String,
    ) {
        val liveTs = Instant.now().epochSecond
        val delayedBody =
            """{"type":"email.delivery_delayed","created_at":"${Instant.ofEpochSecond(liveTs + 5)}",""" +
                """"data":{"email_id":"resend-msg-1","tags":{"invitation_id":"$invitationId"}}}"""
        val badSig =
            client.post("/webhooks/resend") {
                header("svix-id", "msg_bad_sig")
                header("svix-timestamp", liveTs.toString())
                header("svix-signature", "v1,ZmFrZQ==")
                contentType(ContentType.Application.Json)
                setBody(delayedBody)
            }
        assertEquals(HttpStatusCode.Unauthorized, badSig.status)

        postSignedWebhook(h, "msg_delayed_1", liveTs, delayedBody)
        val bouncedBody =
            """{"type":"email.bounced","created_at":"${Instant.ofEpochSecond(liveTs + 10)}",""" +
                """"data":{"email_id":"resend-msg-1","tags":{"invitation_id":"$invitationId"}}}"""
        postSignedWebhook(h, "msg_bounced_1", liveTs, bouncedBody)

        val staleSentBody =
            """{"type":"email.sent","created_at":"${Instant.ofEpochSecond(liveTs + 2)}",""" +
                """"data":{"email_id":"resend-msg-1","tags":{"invitation_id":"$invitationId"}}}"""
        postSignedWebhook(h, "msg_stale_sent_1", liveTs, staleSentBody)

        val listResp = client.get("/admin/invitations") { bearerAuth(ownerBearer) }
        val summary = json.decodeFromString<ApiResponse<List<RegistrationInvitationSummary>>>(listResp.bodyAsText()).data.single()
        assertEquals("BOUNCED", summary.deliveryStatus)
    }

    private suspend fun ApplicationTestBuilder.retryAndConfirmDelivery(
        h: MongoHarness,
        ownerBearer: String,
        ownerSecret: ByteArray,
        invitationId: String,
        firstCode: String,
        capturedEmails: List<CapturedMail>,
    ): String {
        h.clock.current = h.clock.current.plusSeconds(35)
        val retryOpId = "e2e-retry-87654321"
        val retryPrepResp =
            client.post("/admin/invitations/retry/prepare") {
                bearerAuth(ownerBearer)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(PrepareRegistrationInvitationRetryRequest(invitationId, retryOpId)))
            }
        val retryPrep = json.decodeFromString<ApiResponse<RegistrationInvitationPreparation>>(retryPrepResp.bodyAsText()).data
        val retryProof =
            h.issueProof(
                client,
                ownerBearer,
                ownerSecret,
                StepUpAction.ADMIN_INVITE,
                retryPrep.resourceId,
                retryPrep.operationId,
                retryPrep.payloadDigest,
            )
        val retryResp =
            client.post("/admin/invitations/retry") {
                bearerAuth(ownerBearer)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(RetryRegistrationInvitationRequest(invitationId, retryOpId, retryProof)))
            }
        assertEquals(HttpStatusCode.OK, retryResp.status)
        assertEquals(2, capturedEmails.size)
        assertNotEquals(capturedEmails[0].idempotencyKey, capturedEmails[1].idempotencyKey)

        val secondMail = Json.parseToJsonElement(capturedEmails[1].body).jsonObject
        val secondCode =
            Regex("Código de invitación: ([A-Za-z0-9_-]{43})")
                .find(secondMail["text"]!!.jsonPrimitive.content)!!
                .groupValues[1]
        assertNotEquals(firstCode, secondCode)

        val liveTs = Instant.now().epochSecond
        val deliveredBody =
            """{"type":"email.delivered","created_at":"${Instant.ofEpochSecond(liveTs + 25)}",""" +
                """"data":{"email_id":"resend-msg-2","tags":{"invitation_id":"$invitationId"}}}"""
        postSignedWebhook(h, "msg_delivered_2", liveTs, deliveredBody)
        postSignedWebhook(h, "msg_delivered_2", liveTs, deliveredBody)

        val listResp = client.get("/admin/invitations") { bearerAuth(ownerBearer) }
        val summary = json.decodeFromString<ApiResponse<List<RegistrationInvitationSummary>>>(listResp.bodyAsText()).data.single()
        assertEquals("DELIVERED", summary.deliveryStatus)

        val auditResp = client.get("/admin/audit?limit=50") { bearerAuth(ownerBearer) }
        val entries = json.decodeFromString<ApiResponse<List<AdminAuditEntry>>>(auditResp.bodyAsText()).data
        assertTrue(entries.any { it.actorId == "RESEND_WEBHOOK" && it.action == "EMAIL_BOUNCED" && it.outcome == "FAILED" })
        assertTrue(entries.any { it.actorId == "RESEND_WEBHOOK" && it.action == "EMAIL_DELIVERED" && it.outcome == "SUCCEEDED" })
        assertTrue(entries.any { it.action == "INVITATION_LIST" && it.outcome == "SUCCEEDED" })
        assertFalse(entries.joinToString("\n").contains("docente.e2e@example.test"))
        return secondCode
    }

    private suspend fun ApplicationTestBuilder.acceptAndAuthorizeTeacher(
        h: MongoHarness,
        ownerBearer: String,
        ownerSecret: ByteArray,
        invitedId: String,
        teacherBearer: String,
        invitationId: String,
        secondCode: String,
    ) {
        val acceptResp =
            client.post("/auth/invitations/accept") {
                bearerAuth(teacherBearer)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(AcceptRegistrationInvitationRequest(invitationId, secondCode, "e2e-accept-12345678")))
            }
        assertEquals(HttpStatusCode.OK, acceptResp.status)
        assertEquals(setOf(UserRole.STUDENT), h.accounts.findById(invitedId)?.roles)

        h.clock.current = h.clock.current.plusSeconds(35)
        val authOpId = "e2e-authz-12345678"
        val authCmd =
            AuthorizeRegistrationInvitationRequest(
                operationId = authOpId,
                invitationId = invitationId,
                expectedAuthRevision = requireNotNull(h.accounts.findById(invitedId)).authRevision,
                institutionId = "UPTLAX",
                profile = "TEACHER",
                proof = "",
            )
        val authDigest = PrivilegedCommandDigests.invitationAuthorization(invitationId, authCmd)
        val authProof = h.issueProof(client, ownerBearer, ownerSecret, StepUpAction.ADMIN_AUTHORITY, invitationId, authOpId, authDigest)
        val authzResp =
            client.post("/admin/accounts/invitation-authorization") {
                bearerAuth(ownerBearer)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(authCmd.copy(proof = authProof)))
            }
        assertEquals(HttpStatusCode.OK, authzResp.status)
        val authorizedSummary = json.decodeFromString<ApiResponse<AdminUserSummary>>(authzResp.bodyAsText()).data
        assertTrue(UserRole.TEACHER in authorizedSummary.user.roles)
        assertEquals("UPTLAX", authorizedSummary.user.institutionId)
        assertEquals(InvitationStatus.ACTIVE, h.invitations.find(invitationId)?.status)
    }

    private suspend fun ApplicationTestBuilder.postSignedWebhook(
        h: MongoHarness,
        msgId: String,
        tsSeconds: Long,
        body: String,
    ) {
        val resp =
            client.post("/webhooks/resend") {
                header("svix-id", msgId)
                header("svix-timestamp", tsSeconds.toString())
                header("svix-signature", h.signSvix(msgId, tsSeconds, body))
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    private suspend fun assertMongoIndexes(db: MongoDatabase) {
        val inviteIndexes =
            db
                .getCollection<Document>("platform_invitations")
                .listIndexes()
                .toList()
                .mapNotNull { it.getString("name") }
                .toSet()
        assertTrue("invitation_tokenHash_unique" in inviteIndexes)
        assertTrue("invitation_createdAt_desc_idx" in inviteIndexes)
        assertTrue("invitation_institution_createdAt_idx" in inviteIndexes)
        assertTrue("invitation_status_expiresAt_idx" in inviteIndexes)

        val auditIndexes =
            db
                .getCollection<Document>("platform_audit_events")
                .listIndexes()
                .toList()
                .mapNotNull { it.getString("name") }
                .toSet()
        assertTrue("audit_created_desc_idx" in auditIndexes)
        assertTrue("audit_action_created_idx" in auditIndexes)
        assertTrue("audit_actor_created_idx" in auditIndexes)
        assertTrue("audit_resource_created_idx" in auditIndexes)
    }

    @Test
    fun `real Mongo concurrent retry and concurrent post-acceptance authorization allow one winner`(): Unit =
        runBlocking {
            val uri = requireNotNull(System.getenv("V12_TEST_MONGODB_URI"))
            require(uri == "mongodb://127.0.0.1:27017")
            MongoClient.create(uri).use { client ->
                val db = client.getDatabase("invite_race_" + UUID.randomUUID().toString().replace("-", ""))
                try {
                    val now = Instant.parse("2026-11-24T12:00:00Z")
                    val repo = MongoInvitationRepository(db)
                    val invitationId = InvitationToken.generate().value
                    val initialSecret = InvitationToken.generate()
                    assertTrue(
                        repo.create(
                            InvitationRecord(
                                id = invitationId,
                                recipient = "race.teacher@example.test",
                                institutionId = "UPTLAX",
                                profile = InvitationProfile.TEACHER,
                                issuerId = "owner-race",
                                createdAt = now,
                                expiresAt = now.plusSeconds(3600),
                                status = InvitationStatus.SENT,
                                tokenHash = initialSecret.hash,
                            ),
                        ),
                    )

                    val winningTokens = CopyOnWriteArrayList<InvitationToken>()
                    val retryOutcomes =
                        coroutineScope {
                            (1..12)
                                .map {
                                    async {
                                        val candidate = InvitationToken.generate()
                                        val won =
                                            MongoInvitationRepository(db).retry(
                                                id = invitationId,
                                                newTokenHash = candidate.hash,
                                                now = now.plusSeconds(10),
                                                operationId = "shared-retry-op-12345678",
                                            )
                                        if (won) winningTokens += candidate
                                        won
                                    }
                                }.awaitAll()
                        }
                    assertEquals(1, retryOutcomes.count { it })
                    assertEquals(1, winningTokens.size)

                    val accepted =
                        repo.accept(
                            id = invitationId,
                            tokenHash = winningTokens.single().hash,
                            accountEmail = "race.teacher@example.test",
                            emailVerified = true,
                            accountId = "account-race-1",
                            operationId = "accept-race-op-12345678",
                            now = now.plusSeconds(20),
                        )
                    assertNotNull(accepted)
                    assertEquals(InvitationStatus.ACCEPTED, accepted.status)

                    val authorizeOutcomes =
                        coroutineScope {
                            (1..12)
                                .map {
                                    async {
                                        MongoInvitationRepository(db).markAuthorized(
                                            id = invitationId,
                                            now = now.plusSeconds(30),
                                        )
                                    }
                                }.awaitAll()
                        }
                    assertEquals(1, authorizeOutcomes.count { it })
                    assertEquals(InvitationStatus.ACTIVE, repo.find(invitationId)?.status)
                } finally {
                    db.drop()
                }
            }
        }
}
