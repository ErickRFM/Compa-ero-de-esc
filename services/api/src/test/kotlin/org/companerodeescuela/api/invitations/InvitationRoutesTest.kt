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
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
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
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
