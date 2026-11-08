package org.companerodeescuela.api.auth

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.institutions.institutionRoutes
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.mail.VerificationEmailGateway
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.*
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.*

class UniversalRegistrationRoutesTest {
    private val json = Json

    @Test fun `signed in legacy student verifies mailbox without losing roles and old sessions are revoked`() =
        scenario { f ->
            assertTrue(
                f.accounts.create(
                    PlatformAccount(
                        "legacy",
                        "Student",
                        "legacy@example.test",
                        PasswordHasher().hash("test-password"),
                        setOf(UserRole.STUDENT),
                    ),
                ),
            )
            val login = post("/auth/login", """{"username":"legacy@example.test","password":"test-password"}""")
            assertEquals(HttpStatusCode.OK, login.status)
            val previous = json.decodeFromString<ApiResponse<LoginResponse>>(login.bodyAsText()).data
            assertEquals(HttpStatusCode.OK, post("/auth/verification/resend", "{}", previous).status)
            val verified = confirm(previous, assertNotNull(f.mail.tokens["legacy@example.test"]))
            assertEquals("legacy", verified.user.id)
            assertEquals(setOf(UserRole.STUDENT), verified.user.roles)
            assertTrue(verified.user.emailVerified)
            assertEquals(1L, verified.user.authRevision)
            assertEquals(HttpStatusCode.Unauthorized, get("/auth/me", previous).status)
            assertEquals(HttpStatusCode.OK, get("/auth/me", verified).status)
            assertEquals(
                HttpStatusCode.Unauthorized,
                post("/auth/refresh", """{"sessionId":"${previous.sessionId}","refreshToken":"${previous.refreshToken}"}""").status,
            )
        }

    @Test fun `registration yields pending verification without academic authority or public token`() =
        scenario { f ->
            val created = register("student@example.test")
            assertEquals(AccountStatus.PENDING_VERIFICATION, created.user.accountStatus)
            assertTrue(created.user.roles.isEmpty())
            assertEquals(HttpStatusCode.OK, get("/auth/me", created).status)
            assertEquals(HttpStatusCode.Unauthorized, get("/private-probe", created).status)
            assertNotNull(f.mail.tokens["student@example.test"])
            val me = get("/auth/me", created).bodyAsText()
            assertFalse(me.contains(assertNotNull(f.mail.tokens["student@example.test"])))
            assertFalse(me.contains("passwordHash"))
        }

    @Test fun `verified student receives basic role and prior pending access refresh become unusable`() =
        scenario { f ->
            val pending = register("student@example.test")
            val verified = confirm(pending, assertNotNull(f.mail.tokens["student@example.test"]))
            assertEquals(AccountStatus.ACTIVE, verified.user.accountStatus)
            assertEquals(setOf(UserRole.STUDENT), verified.user.roles)
            assertEquals(1L, verified.user.authRevision)
            assertEquals(HttpStatusCode.Unauthorized, get("/auth/me", pending).status)
            assertEquals(HttpStatusCode.OK, get("/auth/me", verified).status)
            val restored = json.decodeFromString<ApiResponse<UserSummary>>(get("/auth/me", verified).bodyAsText()).data
            assertTrue(restored.emailVerified, "Restoring the native session must retain the verified email state")
            assertEquals(RegistrationAccountType.STUDENT, restored.registrationAccountType)
            assertEquals(
                HttpStatusCode.Unauthorized,
                post("/auth/refresh", """{"sessionId":"${pending.sessionId}","refreshToken":"${pending.refreshToken}"}""").status,
            )
        }

    @Test fun `teacher and tutor verified requests confer no teacher tutor group or tenant grant`() =
        scenario { f ->
            for (profile in listOf("TEACHER", "TUTOR")) {
                val email = profile.lowercase() + "@example.test"
                val pending =
                    register(
                        email,
                        profile,
                        ",\"requestedInstitutionId\":\"test-campus\",\"identityReference\":\"professional-reference\",\"preferredGroup\":\"requested-group\"",
                    )
                assertEquals(AccountStatus.PENDING_VERIFICATION, pending.user.accountStatus)
                val verified = confirm(pending, assertNotNull(f.mail.tokens[email]))
                assertEquals(AccountStatus.PENDING_APPROVAL, verified.user.accountStatus)
                assertTrue(verified.user.roles.isEmpty())
                val account = assertNotNull(f.accounts.findById(verified.user.id))
                assertTrue(account.roles.isEmpty())
                assertNull(account.institutionId)
                assertEquals(HttpStatusCode.Unauthorized, get("/private-probe", verified).status)
            }
        }

    @Test fun `verified external participant has no academic role`() =
        scenario { f ->
            val pending = register("visitor@example.test", "PARTICIPANT")
            val verified = confirm(pending, assertNotNull(f.mail.tokens["visitor@example.test"]))
            assertEquals(AccountStatus.ACTIVE, verified.user.accountStatus)
            assertEquals(
                setOf("WORKSHOP_PARTICIPANT"),
                verified.user.roles
                    .map { it.name }
                    .toSet(),
            )
            assertFalse(verified.user.roles.any { it.isStaff || it == UserRole.STUDENT })
            assertNull(verified.user.institutionId)
            assertEquals(HttpStatusCode.Unauthorized, get("/private-probe", verified).status)
        }

    @Test fun `owner registration requires mail-bound code and verification before privileges`() {
        val code = "t".repeat(43)
        val hash =
            MessageDigest
                .getInstance("SHA-256")
                .digest(code.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        val env =
            mapOf(
                "APP_ENV" to "PRODUCTION",
                "MONGODB_DATABASE" to "companero_prod",
                "PROD_OWNER_ISOLATION_CERTIFIED" to "true",
                "OWNER_INVITE_EMAIL" to "owner@example.test",
                "OWNER_INVITE_CODE_SHA256" to hash,
                "OWNER_INVITE_EXPIRES_AT" to (Instant.now().epochSecond + 3600).toString(),
            )
        scenario(env) { f ->
            val payload = ",\"ownerInvitationCode\":\"$code\""
            assertEquals(
                HttpStatusCode.Forbidden,
                post("/auth/register", registrationBody("other@example.test", "OWNER_INVITED", payload)).status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                post(
                    "/auth/register",
                    registrationBody(
                        "owner@example.test",
                        "OWNER_INVITED",
                        ",\"ownerInvitationCode\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
                    ),
                ).status,
            )
            val pending = register("owner@example.test", "OWNER_INVITED", ",\"ownerInvitationCode\":\"$code\")
            assertTrue(pending.user.roles.isEmpty())
            val verified = confirm(pending, assertNotNull(f.mail.tokens["owner@example.test"]))
            assertEquals(AccountStatus.ACTIVE, verified.user.accountStatus)
            assertEquals(setOf(UserRole.WORKSHOP_PARTICIPANT), verified.user.roles)
            assertFalse(UserRole.SUPER_ADMIN in verified.user.roles)
        }
    }

    @Test fun `owner registration normalizes production settings consistently with the settings loader`() {
        val code = "n".repeat(43)
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(code.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        val expiresAt = Instant.now().epochSecond + 3600
        val env =
            mapOf(
                "APP_ENV" to " PRODUCTION ",
                "MONGODB_DATABASE" to "companero_prod ",
                "PROD_OWNER_ISOLATION_CERTIFIED" to "true",
                "OWNER_INVITE_EMAIL" to " OWNER@EXAMPLE.TEST ",
                "OWNER_INVITE_CODE_SHA256" to " $digest ",
                "OWNER_INVITE_EXPIRES_AT" to " $expiresAt ",
            )

        scenario(env) { f ->
            val pending = register("owner@example.test", "OWNER_INVITED", ",\\"ownerInvitationCode\\":\\"$code\\"")
            assertTrue(pending.user.roles.isEmpty())
            val verified = confirm(pending, assertNotNull(f.mail.tokens["owner@example.test"]))
            assertEquals(AccountStatus.ACTIVE, verified.user.accountStatus)
            assertEquals(setOf(UserRole.WORKSHOP_PARTICIPANT), verified.user.roles)
            assertFalse(UserRole.SUPER_ADMIN in verified.user.roles)
        }
    }

    @Test fun `owner invitation does not consume its code when an existing unverified student identity blocks enrollment`() {
        val code = "r".repeat(43)
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(code.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        val expiresAt = Instant.now().epochSecond + 3600
        val env =
            mapOf(
                "APP_ENV" to "production",
                "MONGODB_DATABASE" to "companero_prod",
                "PROD_OWNER_ISOLATION_CERTIFIED" to "true",
                "OWNER_INVITE_EMAIL" to "owner@example.test",
                "OWNER_INVITE_CODE_SHA256" to digest,
                "OWNER_INVITE_EXPIRES_AT" to expiresAt.toString(),
            )

        scenario(env) { f ->
            val existing = register("owner@example.test", "STUDENT")
            val response =
                post(
                    "/auth/register",
                    registrationBody(
                        "owner@example.test",
                        "OWNER_INVITED",
                        ",\"ownerInvitationCode\":\"$code\"",
                    ),
                )

            assertEquals(HttpStatusCode.Conflict, response.status)
            val stored = assertNotNull(f.accounts.findByIdentifier("owner@example.test"))
            assertEquals(existing.user.id, stored.id)
            assertEquals(RegistrationAccountType.STUDENT, stored.registrationIntent?.profile)
            assertNull(stored.emailVerifiedAt)
            assertTrue(
                f.ownerInvitations.consume(
                    digest,
                    "owner@example.test",
                    Instant.ofEpochSecond(expiresAt),
                    f.clock.instant(),
                    "collision-probe",
                ),
                "A duplicate-account rejection must happen before the one-time invitation is consumed",
            )
        }
    }

    @Test fun `owner registration without production invitation cannot create an account`() =
        scenario { f ->
            val result =
                post(
                    "/auth/register",
                    registrationBody(
                        "owner@example.test",
                        "OWNER_INVITED",
                        ",\"ownerInvitationCode\":\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"",
                    ),
                )
            assertEquals(HttpStatusCode.Forbidden, result.status)
            assertNull(f.accounts.findByIdentifier("owner@example.test"))
        }

    @Test fun `public privileged profile and unknown requested institutions are rejected`() =
        scenario { _ ->
            for (profile in listOf("ADMIN", "SUPER_ADMIN")) {
                assertEquals(
                    HttpStatusCode.BadRequest,
                    post("/auth/register", registrationBody(profile.lowercase() + "@example.test", profile)).status,
                )
            }
            assertEquals(
                HttpStatusCode.BadRequest,
                post(
                    "/auth/register",
                    registrationBody(
                        "teacher@example.test",
                        "TEACHER",
                        ",\"requestedInstitutionId\":\"unknown\",\"identityReference\":\"reference\"",
                    ),
                ).status,
            )
        }

    @Test fun `token belongs to one account expires and is consumed once`() =
        scenario { f ->
            val first = register("first@example.test")
            val second = register("second@example.test")
            val firstToken = assertNotNull(f.mail.tokens["first@example.test"])
            assertEquals(HttpStatusCode.BadRequest, confirmResponse(second, firstToken).status)
            assertEquals(HttpStatusCode.BadRequest, confirmResponse(first, "forged-token").status)
            val verified = confirm(first, firstToken)
            assertEquals(HttpStatusCode.BadRequest, confirmResponse(verified, firstToken).status)
            f.clock.advance(3601)
            assertEquals(HttpStatusCode.BadRequest, confirmResponse(second, assertNotNull(f.mail.tokens["second@example.test"])).status)
            assertEquals(AccountStatus.PENDING_VERIFICATION, f.accounts.findById(second.user.id)?.accountStatus)
        }

    @Test fun `suspension prevents pending email confirmation and refresh`() =
        scenario { f ->
            val pending = register("pending@example.test")
            f.accounts.changeIdentity(
                pending.user.id,
                0,
                AccountIdentityChange("suspend", "operator", AccountStatus.SUSPENDED, emptySet(), null, Instant.now()),
            )
            assertEquals(HttpStatusCode.Unauthorized, confirmResponse(pending, assertNotNull(f.mail.tokens["pending@example.test"])).status)
            assertEquals(AccountStatus.SUSPENDED, f.accounts.findById(pending.user.id)?.accountStatus)
        }

    @Test fun `resend obeys persisted cooldown and token rotation invalidates previous challenge`() =
        scenario { f ->
            val pending = register("pending@example.test")
            val original = assertNotNull(f.mail.tokens["pending@example.test"])
            val cooldown = post("/auth/verification/resend", "{}", pending)
            assertEquals(HttpStatusCode.TooManyRequests, cooldown.status)
            assertEquals("60", cooldown.headers[HttpHeaders.RetryAfter])
            f.clock.advance(61)
            assertEquals(HttpStatusCode.OK, post("/auth/verification/resend", "{}", pending).status)
            assertNotEquals(original, f.mail.tokens["pending@example.test"])
            assertEquals(HttpStatusCode.BadRequest, confirmResponse(pending, original).status)
            confirm(pending, assertNotNull(f.mail.tokens["pending@example.test"]))
        }

    @Test fun `institution directory exposes actual enabled records without granting scope`() =
        scenario { _ ->
            val response = client.get("/institutions")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                listOf(InstitutionSummary("test-campus", "Test campus")),
                json.decodeFromString<ApiResponse<List<InstitutionSummary>>>(response.bodyAsText()).data,
            )
        }

    @Test fun `oversized registration JSON is rejected before account creation`() =
        scenario { f ->
            val body = registrationBody("oversized@example.test", "STUDENT").dropLast(1) + ",\"extra\":\"" + "x".repeat(15_000) + "\"}"
            assertEquals(HttpStatusCode.BadRequest, post("/auth/register", body).status)
            assertNull(f.accounts.findByIdentifier("oversized@example.test"))
        }

    @Test fun `normalized duplicate email does not create another identity`() =
        scenario { f ->
            register("unique@example.test")
            assertEquals(HttpStatusCode.Conflict, post("/auth/register", registrationBody(" UNIQUE@EXAMPLE.TEST ", "STUDENT")).status)
            assertNotNull(f.accounts.findByIdentifier("unique@example.test"))
        }

    @Test fun `changing email cannot bypass address registration throttling`() =
        scenario { _ ->
            repeat(20) { n -> register("signup$n@example.test") }
            assertEquals(HttpStatusCode.TooManyRequests, post("/auth/register", registrationBody("another@example.test", "STUDENT")).status)
        }

    private class TestClock : Clock() {
        var current = Instant.now()

        override fun instant() = current

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        fun advance(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }

    private class CapturedMail : VerificationEmailGateway {
        val tokens = mutableMapOf<String, String>()

        override suspend fun send(
            recipient: String,
            token: String,
            operationId: String,
        ): VerificationDeliveryStatus {
            tokens[recipient] = token
            return VerificationDeliveryStatus.ACCEPTED
        }
    }

    private class Fixture {
        val accounts = InMemoryPlatformAccountRepository()
        val sessions = InMemoryRefreshSessionRepository()
        val mail = CapturedMail()
        val ownerInvitations = InMemoryOwnerInvitationRepository()
        val clock = TestClock()
        val institutions = InMemoryInstitutionRepository(listOf(InstitutionSummary("test-campus", "Test campus")))
    }

    private fun scenario(
        ownerEnv: Map<String, String> = emptyMap(),
        block: suspend ApplicationTestBuilder.(Fixture) -> Unit,
    ) = testApplication {
        val f = Fixture()
        val settings = TestFixtures.settings().copy(jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray())
        val identity = MockIdentityProvider()
        application {
            configurePlugins(settings, f.sessions, PlatformSessionAuthority(f.accounts, identity))
            routing {
                authRoutes(
                    settings,
                    identity,
                    sessions = f.sessions,
                    accounts = f.accounts,
                    institutions = f.institutions,
                    verificationEmail = f.mail,
                    verificationClock = f.clock,
                    ownerInviteEnvironment = ownerEnv::get,
                    ownerInvitationRepository = f.ownerInvitations,
                )
                institutionRoutes(f.institutions)
                authenticate(AuthTokenService.PROVIDER_NAME) { get("/private-probe") { call.respond(HttpStatusCode.OK) } }
            }
        }
        block(f)
    }

    private fun registrationBody(
        email: String,
        profile: String = "STUDENT",
        extra: String = "",
    ) = """{"displayName":"QA User","email":"$email","password":"test-password","accountType":"$profile"$extra}"""

    private suspend fun ApplicationTestBuilder.register(
        email: String,
        profile: String = "STUDENT",
        extra: String = "",
    ): LoginResponse {
        val response = post("/auth/register", registrationBody(email, profile, extra))
        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString<ApiResponse<LoginResponse>>(response.bodyAsText()).data
    }

    private suspend fun ApplicationTestBuilder.post(
        path: String,
        body: String,
        session: LoginResponse? = null,
    ) = client.post(path) {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        session?.let {
            header(
                HttpHeaders.Authorization,
                "Bearer " + it.accessToken,
            )
        }
        setBody(body)
    }

    private suspend fun ApplicationTestBuilder.get(
        path: String,
        session: LoginResponse,
    ) = client.get(path) {
        header(
            HttpHeaders.Authorization,
            "Bearer " + session.accessToken,
        )
    }

    private suspend fun ApplicationTestBuilder.confirmResponse(
        session: LoginResponse,
        token: String,
    ) = post("/auth/verification/confirm", """{"token":"$token"}""", session)

    private suspend fun ApplicationTestBuilder.confirm(
        session: LoginResponse,
        token: String,
    ): LoginResponse {
        val response = confirmResponse(session, token)
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString<ApiResponse<LoginResponse>>(response.bodyAsText()).data
    }
}
