package org.companerodeescuela.api.coordination

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.attendance.InMemoryAttendanceRepository
import org.companerodeescuela.api.auth.AccountIdentityChange
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.auth.RefreshSession
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.CoordinationOverview
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DirectionOverviewRoutesTest {
    private class Fixture {
        val settings = TestFixtures.settings().copy(jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray())
        val accounts = InMemoryPlatformAccountRepository()
        val sessions = InMemoryRefreshSessionRepository()
        val groups = InMemoryAcademicGroupRepository()
        val identity = MockIdentityProvider()
        val tokens = AuthTokenService(settings)
        val attendance = InMemoryAttendanceRepository()

        suspend fun session(
            id: String,
            roles: Set<UserRole> = setOf(UserRole.PRINCIPAL),
            capabilities: Set<AdminCapability> = setOf(AdminCapability.DIRECTIVE_SUPERVISION),
            institution: String? = "campus",
            verified: Boolean = true,
        ): String {
            val now = Instant.now().minusSeconds(1)
            val account =
                PlatformAccount(
                    id,
                    "QA Direction",
                    "$id@example.test",
                    "test-hash",
                    roles,
                    institutionId = institution,
                    emailVerifiedAt = now.takeIf { verified },
                    adminCapabilities = capabilities,
                )
            accounts.create(account)
            val user = account.toUserSummary()
            sessions.create(
                RefreshSession(
                    "$id-session",
                    "test-hash",
                    user,
                    now,
                    now.plusSeconds(3600),
                    identitySource = SessionIdentitySource.NATIVE,
                ),
            )
            return tokens.issue(user, "$id-session").value
        }
    }

    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        testApplication {
            val fixture = Fixture()
            application {
                configurePlugins(fixture.settings, fixture.sessions, PlatformSessionAuthority(fixture.accounts, fixture.identity))
                routing { coordinationRoutes(fixture.settings, fixture.groups, fixture.accounts, fixture.attendance) }
            }
            block(fixture)
        }

    @Test
    fun `direction supervision reads only its current institution and never gains management by role`() =
        scenario { f ->
            val token = f.session("direction")
            val now = Instant.now().minusSeconds(1)
            f.groups.create(AcademicGroupRecord("9A", "9A", true, now, "campus", "period", now, now.plusSeconds(3600)))
            f.groups.create(AcademicGroupRecord("foreign", "Foreign", true, now, "other", "period", now, now.plusSeconds(3600)))
            val pass =
                AttendanceSessionResponse(
                    "pass",
                    "occurrence",
                    "course",
                    "9A",
                    "2026-10-28",
                    "07:00",
                    "08:00",
                    "teacher",
                    now.epochSecond,
                    now.plusSeconds(600).epochSecond,
                    status = AttendanceSessionStatus.OPEN,
                    identitySource = AttendanceIdentitySource.NATIVE,
                    institutionId = "campus",
                    groupId = "group",
                    groupScopeRevision = 1,
                )
            f.attendance.createSession(pass)
            f.attendance.createSession(pass.copy(id = "foreign-pass", occurrenceId = "foreign-occurrence", institutionId = "other"))
            f.attendance.writeAttempt(
                AttendanceRecordResponse(
                    "record",
                    "operation",
                    pass.id,
                    pass.occurrenceId,
                    "private-student",
                    AttendanceStatus.REVIEW_REQUIRED,
                    AttendanceReasonCode.CLASS_CALL_CONFIRMED,
                    now.epochSecond,
                    now.epochSecond,
                ),
            )
            val response = client.get("/coordination/overview") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertEquals(1, Json.decodeFromString<ApiResponse<CoordinationOverview>>(response.bodyAsText()).data.activeGroups)
            val overview = Json.decodeFromString<ApiResponse<CoordinationOverview>>(response.bodyAsText()).data
            assertEquals(1, overview.openAttendancePasses)
            assertEquals(1L, overview.pendingAttendanceReviews)
            assertEquals(1000, overview.attendanceSessionsSampleLimit)
            assertEquals(false, overview.attendanceSampleLimited)
            assertEquals(false, response.bodyAsText().contains("private-student"))
            assertEquals(setOf(AdminCapability.DIRECTIVE_SUPERVISION), f.accounts.findById("direction")?.adminCapabilities)
        }

    @Test
    fun `direction without explicit supervision or verified institution is forbidden`() =
        scenario { f ->
            val tokens =
                listOf(
                    f.session("no-grant", capabilities = emptySet()),
                    f.session("no-institution", institution = null),
                    f.session("unverified", verified = false),
                    f.session("student", roles = setOf(UserRole.STUDENT)),
                )
            for (token in tokens) {
                assertEquals(
                    HttpStatusCode.Forbidden,
                    client.get("/coordination/overview") { header(HttpHeaders.Authorization, "Bearer $token") }.status,
                )
            }
        }

    @Test
    fun `revoked direction authority cannot keep reading with old session`() =
        scenario { f ->
            val token = f.session("revoked")
            f.accounts.changeIdentity(
                "revoked",
                0,
                AccountIdentityChange("revoke", "owner", AccountStatus.SUSPENDED, setOf(UserRole.PRINCIPAL), "campus", Instant.now()),
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/coordination/overview") { header(HttpHeaders.Authorization, "Bearer $token") }.status,
            )
            val capabilityToken = f.session("capability-revoked")
            val changed =
                assertNotNull(
                    f.accounts.changeIdentity(
                        "capability-revoked",
                        0,
                        AccountIdentityChange(
                            "remove-supervision",
                            "owner",
                            AccountStatus.ACTIVE,
                            setOf(UserRole.PRINCIPAL),
                            "campus",
                            Instant.now(),
                            capabilities = emptySet(),
                        ),
                    ),
                )
            assertEquals(setOf(UserRole.PRINCIPAL), changed.roles)
            assertEquals(AccountStatus.ACTIVE, changed.accountStatus)
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/coordination/overview") { header(HttpHeaders.Authorization, "Bearer $capabilityToken") }.status,
            )
            val now = Instant.now()
            val currentUser = changed.toUserSummary()
            val currentSessionId = "capability-revoked-current-session"
            f.sessions.create(
                RefreshSession(
                    currentSessionId,
                    "test-hash",
                    currentUser,
                    now,
                    now.plusSeconds(3600),
                    identitySource = SessionIdentitySource.NATIVE,
                ),
            )
            val currentToken = f.tokens.issue(currentUser, currentSessionId).value
            assertEquals(
                HttpStatusCode.Forbidden,
                client.get("/coordination/overview") { header(HttpHeaders.Authorization, "Bearer $currentToken") }.status,
            )
        }

    @Test
    fun `existing coordinator can still read its scoped overview without direction capabilities`() =
        scenario { f ->
            val token = f.session("coordinator", roles = setOf(UserRole.COORDINATOR), capabilities = emptySet())
            assertEquals(
                HttpStatusCode.OK,
                client.get("/coordination/overview") { header(HttpHeaders.Authorization, "Bearer $token") }.status,
            )
        }
}
