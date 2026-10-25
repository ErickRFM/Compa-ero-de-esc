package org.companerodeescuela.api.attendance

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.AccountIdentityChange
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.auth.RefreshSession
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.classroom.NativeClassroom
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.integrations.mock.MockFixtures
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.test.ScopedAcademicFixtures
import org.companerodeescuela.api.test.TestFixtures
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ClassroomStatus
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceNotificationEnqueueResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AttendanceNativeRoutesTest {
    private class Fixture {
        val now = Instant.now()
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val admin = UserSummary("scoped-admin", "Admin", roles = setOf(UserRole.ADMIN), institutionId = "campus")
        val teacher = UserSummary(MockFixtures.TEACHER_ID, "Teacher", roles = setOf(UserRole.TEACHER), institutionId = "campus")
        val student = UserSummary("student-test", "Student", roles = setOf(UserRole.STUDENT), institutionId = "campus")
        val accounts = InMemoryPlatformAccountRepository()
        val sessions = InMemoryRefreshSessionRepository()
        val settings = TestFixtures.settings().copy(jwtSecret = "0123456789abcdef0123456789abcdef".toCharArray())
        val tokens = AuthTokenService(settings)
        val identity = MockIdentityProvider()
        val repository = InMemoryAttendanceRepository()
        val groups = InMemoryAcademicGroupRepository()
        val classes = InMemoryClassroomRepository()
        val resolver = ProviderAttendanceOccurrenceResolver(MockAcademicProvider())
        val policy =
            AttendanceAccessPolicy(
                repository,
                resolver,
                AttendanceAdministrationPolicy(accounts, ClassroomService(classes, groups, clock, accounts = accounts)),
            )
        val service = AttendanceSessionService(repository, resolver, policy, clock)

        suspend fun initialize() {
            accounts.create(ScopedAcademicFixtures.account(admin))
            accounts.create(ScopedAcademicFixtures.account(teacher))
            accounts.create(ScopedAcademicFixtures.account(student))
            groups.create(ScopedAcademicFixtures.group("6A").copy(institutionId = "campus"))
            groups.create(ScopedAcademicFixtures.group("6B").copy(institutionId = "other"))
            classes.createClassroom(
                NativeClassroom("own-class", "Own", null, null, "6A", "6A", teacher.id, "Teacher", ClassroomStatus.ACTIVE, now, 0, 0),
            )
            classes.createClassroom(
                NativeClassroom(
                    "foreign-class",
                    "Foreign",
                    null,
                    null,
                    "6B",
                    "6B",
                    "outside",
                    "Outside",
                    ClassroomStatus.ACTIVE,
                    now,
                    0,
                    0,
                ),
            )
            for ((id, course, owner, group) in listOf(
                listOf("own", "own-class", teacher.id, "6A"),
                listOf("foreign", "foreign-class", "outside", "6B"),
            )) {
                repository.createSession(
                    AttendanceSessionResponse(
                        id,
                        "occurrence-$id",
                        course,
                        group,
                        LocalDate.now(ZoneOffset.UTC).toString(),
                        "07:00",
                        "07:50",
                        owner,
                        now.epochSecond,
                        now.plusSeconds(3600).epochSecond,
                        status = AttendanceSessionStatus.OPEN,
                        identitySource = AttendanceIdentitySource.NATIVE,
                        institutionId =
                            if (id ==
                                "foreign"
                            ) {
                                "other"
                            } else {
                                "campus"
                            },
                        groupId = group,
                        groupScopeRevision = 0,
                    ),
                )
            }
        }

        suspend fun token(
            actor: UserSummary,
            source: SessionIdentitySource = SessionIdentitySource.NATIVE,
        ): String {
            val id = actor.id + source.name
            sessions.create(RefreshSession(id, "test-hash", actor, now, now.plusSeconds(3600), identitySource = source))
            return tokens.issue(actor, id).value
        }
    }

    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture) -> Unit) =
        testApplication {
            val f = Fixture()
            runBlocking { f.initialize() }
            application {
                configurePlugins(f.settings, f.sessions, PlatformSessionAuthority(f.accounts, f.identity))
                routing {
                    attendanceRoutes(
                        f.settings,
                        f.service,
                        AttendanceStudentService(f.repository, AttendanceEnrollmentResolver(MockAcademicProvider())),
                        AttendanceReviewService(f.repository, f.policy, f.clock),
                        AttendanceQrService(
                            CharArray(32) { 's' },
                            f.repository,
                            f.clock,
                            accessPolicy = f.policy,
                        ),
                    )
                }
            }
            block(f)
        }

    @Test fun `actual native admin lists and operates only its persisted classroom sessions`() =
        scenario { f ->
            val token = f.token(f.admin)
            val response = client.get("/attendance/sessions/open") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                listOf("own"),
                Json.decodeFromString<ApiResponse<List<AttendanceSessionResponse>>>(response.bodyAsText()).data.map { it.id },
            )
            assertEquals(HttpStatusCode.OK, client.get("/attendance/sessions/own/roster") { bearerAuth(token) }.status)
            assertEquals(HttpStatusCode.Forbidden, client.get("/attendance/sessions/foreign/roster") { bearerAuth(token) }.status)
            assertEquals(HttpStatusCode.Forbidden, client.post("/attendance/sessions/foreign/close") { bearerAuth(token) }.status)
            assertEquals(AttendanceSessionStatus.OPEN, f.repository.findSession("foreign")!!.status)
            assertEquals(HttpStatusCode.OK, client.post("/attendance/sessions/own/qr") { bearerAuth(token) }.status)
        }

    @Test fun `source verified by session prevents same ID institutional access to native attendance`() =
        scenario { f ->
            val native = f.token(f.teacher)
            val external = f.token(f.teacher, SessionIdentitySource.INSTITUTIONAL)
            assertEquals(HttpStatusCode.OK, client.get("/attendance/sessions/own/roster") { bearerAuth(native) }.status)
            assertEquals(HttpStatusCode.Forbidden, client.get("/attendance/sessions/own/roster") { bearerAuth(external) }.status)
            assertEquals(HttpStatusCode.Forbidden, client.get("/attendance/sessions/open") { bearerAuth(native) }.status)
            val current = assertNotNull(f.accounts.findById(f.teacher.id))
            assertNotNull(
                f.accounts.changeIdentity(
                    current.id,
                    current.authRevision,
                    AccountIdentityChange("suspend", "security", AccountStatus.SUSPENDED, current.roles, current.institutionId, f.now),
                ),
            )
            assertEquals(HttpStatusCode.Unauthorized, client.get("/attendance/sessions/own/roster") { bearerAuth(native) }.status)
        }

    @Test fun `reminder endpoint is teacher-only, hides missing sessions and rejects closed passes`() =
        scenario { f ->
            val teacher = f.token(f.teacher)
            val student = f.token(f.student)
            val response = client.post("/attendance/sessions/own/reminders") { bearerAuth(teacher) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                0,
                Json.decodeFromString<ApiResponse<AttendanceNotificationEnqueueResponse>>(response.bodyAsText()).data.queuedDevices,
            )
            assertEquals(HttpStatusCode.Forbidden, client.post("/attendance/sessions/own/reminders") { bearerAuth(student) }.status)
            assertEquals(HttpStatusCode.NotFound, client.post("/attendance/sessions/missing/reminders") { bearerAuth(teacher) }.status)
            assertEquals(HttpStatusCode.OK, client.post("/attendance/sessions/own/close") { bearerAuth(teacher) }.status)
            assertEquals(HttpStatusCode.Conflict, client.post("/attendance/sessions/own/reminders") { bearerAuth(teacher) }.status)
        }

    @Test fun `teacher history is current-scope filtered and never exposes another classroom`() =
        scenario { f ->
            val teacher = f.token(f.teacher)
            val student = f.token(f.student)
            val response = client.get("/attendance/sessions/history") { bearerAuth(teacher) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                listOf("own"),
                Json.decodeFromString<ApiResponse<List<AttendanceSessionResponse>>>(response.bodyAsText()).data.map { it.id },
            )
            assertEquals(HttpStatusCode.Forbidden, client.get("/attendance/sessions/history") { bearerAuth(student) }.status)
        }
}
