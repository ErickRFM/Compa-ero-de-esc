package org.companerodeescuela.api.academic

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.attendance.AttendanceAccessPolicy
import org.companerodeescuela.api.attendance.AttendanceAdministrationPolicy
import org.companerodeescuela.api.attendance.AttendanceEnrollmentResolver
import org.companerodeescuela.api.attendance.AttendanceReviewService
import org.companerodeescuela.api.attendance.AttendanceSessionService
import org.companerodeescuela.api.attendance.AttendanceStudentService
import org.companerodeescuela.api.attendance.InMemoryAttendanceRepository
import org.companerodeescuela.api.attendance.ProviderAttendanceOccurrenceResolver
import org.companerodeescuela.api.attendance.SiiiAttendanceAuthority
import org.companerodeescuela.api.attendance.attendanceRoutes
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.devices.InMemoryDeviceTokenRepository
import org.companerodeescuela.api.devices.InMemoryPushOutboxRepository
import org.companerodeescuela.api.devices.PushDeliveryResult
import org.companerodeescuela.api.devices.PushGateway
import org.companerodeescuela.api.devices.PushOutboxService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.integrations.mock.MockIdentityProvider
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.presence.InMemorySchoolPresenceRepository
import org.companerodeescuela.api.presence.SchoolPresencePolicy
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.ClassCallConfirmationRequest
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest
import org.companerodeescuela.shared.contracts.UserRole
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SiiiAttendanceTest {
    @Test
    fun `SIII HTTP pass queues notice confirms without class QR and immediately rejects revoked authority`() =
        testApplication {
            val f = SiiiAcademicRoutesTest.Fixture().also { it.initialize() }
            val authority = SiiiAttendanceAuthority(f.service, f.reader, f.links, f.identities, f.accounts)
            val groups = InMemoryAcademicGroupRepository()
            val classrooms = ClassroomService(InMemoryClassroomRepository(), groups, accounts = f.accounts)
            val projection =
                NativeAcademicProjection(
                    f.accounts,
                    classrooms,
                    groups,
                    InMemoryAcademicScheduleOverrideRepository(),
                    institutionalAttendance = authority,
                )
            val repository = InMemoryAttendanceRepository()
            val resolver = ProviderAttendanceOccurrenceResolver(MockAcademicProvider())
            val access = AttendanceAccessPolicy(repository, resolver, AttendanceAdministrationPolicy(f.accounts, classrooms, projection))
            val clock = Clock.fixed(f.now, ZoneOffset.UTC)
            val occurrence =
                f.snapshot.occurrences.single().copy(
                    date =
                        f.now
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toString(),
                    startsAt = f.now.minusSeconds(600).toString(),
                    endsAt = f.now.plusSeconds(1800).toString(),
                )
            f.stored = f.stored.copy(snapshot = f.snapshot.copy(occurrences = listOf(occurrence)))
            val devices = InMemoryDeviceTokenRepository()
            val token = "fcm_synthetic_student_device_123456"
            devices.register("NATIVE:student", token, "android", null, "session-student")
            var delivered: Map<String, String>? = null
            val push =
                PushOutboxService(
                    devices,
                    InMemoryPushOutboxRepository(),
                    object : PushGateway {
                        override suspend fun send(
                            token: String,
                            title: String,
                            body: String,
                        ) = error("Attendance must include its session action")

                        override suspend fun send(
                            token: String,
                            title: String,
                            body: String,
                            data: Map<String, String>,
                        ): PushDeliveryResult {
                            delivered = data
                            return PushDeliveryResult.ACCEPTED
                        }
                    },
                    clock,
                    authorizeRecipient = { it == "NATIVE:student" },
                    authorizeSession = {
                        owner,
                        value,
                        ->
                        owner == "NATIVE:student" && value == token
                    },
                )
            val sessions = AttendanceSessionService(repository, resolver, access, clock, nativeProjection = projection, pushService = push)
            val entry = "synthetic-institution-entry-qr"
            val hash =
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(entry.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            val presence =
                SchoolPresenceService(
                    InMemorySchoolPresenceRepository(),
                    SchoolPresencePolicy(hash, setOf("QA-CAMPUS"), emptySet(), institutionId = "tenant-a"),
                    clock = clock,
                    accounts = f.accounts,
                )
            val students =
                AttendanceStudentService(
                    repository,
                    AttendanceEnrollmentResolver(MockAcademicProvider(), classrooms, authority),
                    schoolPresenceService = presence,
                    clock = clock,
                    nativeProjection = projection,
                )
            val teacher = f.accounts.findById("teacher")!!.toUserSummary()
            val student = f.accounts.findById("student")!!.toUserSummary()
            val request = CreateAttendanceSessionRequest(occurrence.sourceId, occurrence.date, 10)
            application {
                configurePlugins(f.settings, f.sessions, PlatformSessionAuthority(f.accounts, MockIdentityProvider()))
                routing { attendanceRoutes(f.settings, sessions, students, AttendanceReviewService(repository, access)) }
            }

            suspend fun post(
                path: String,
                who: String,
                body: String,
            ) = client.post(path) {
                header(HttpHeaders.Authorization, "Bearer ${f.tokens.getValue(who)}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            suspend fun get(
                path: String,
                who: String,
            ) = client.get(path) { header(HttpHeaders.Authorization, "Bearer ${f.tokens.getValue(who)}") }
            assertEquals(HttpStatusCode.Unauthorized, client.get("/attendance/sessions/active").status)
            assertEquals(HttpStatusCode.Forbidden, post("/attendance/sessions", "student", Json.encodeToString(request)).status)
            val response = post("/attendance/sessions", "teacher", Json.encodeToString(request))
            assertEquals(HttpStatusCode.OK, response.status)
            val session = Json.decodeFromString<ApiResponse<AttendanceSessionResponse>>(response.bodyAsText()).data
            assertEquals("SIII", session.academicSource)
            assertEquals(f.sectionId, session.courseId)
            assertEquals(1L, session.groupScopeRevision)
            assertEquals(listOf(session), students.discoverActiveFor(student.id, student))
            assertEquals(listOf("student"), authority.studentIdsForTeacher(teacher, f.sectionId))
            assertEquals(session, sessions.roster(teacher.id, session.id, nativeActor = teacher).session)
            assertEquals(null, delivered, "Opening must enqueue without invoking FCM inline")
            assertEquals(true, push.dispatchNext())
            assertEquals(mapOf("type" to "attendance_session_opened", "sessionId" to session.id), assertNotNull(delivered))
            presence.start(
                student.id,
                StartSchoolPresenceRequest("entry", entry, SchoolNetworkEvidence(ssid = "QA-CAMPUS"), f.now.epochSecond),
                student,
            )
            val confirmed =
                post(
                    "/attendance/sessions/${session.id}/confirm",
                    "student",
                    Json.encodeToString(ClassCallConfirmationRequest("confirm", SchoolNetworkEvidence(ssid = "QA-CAMPUS"))),
                )
            assertEquals(HttpStatusCode.OK, confirmed.status)
            val record = Json.decodeFromString<ApiResponse<AttendanceRecordResponse>>(confirmed.bodyAsText()).data
            assertEquals(AttendanceStatus.REVIEW_REQUIRED, record.status, "Synthetic network evidence never certifies physical attendance")
            assertEquals(AttendanceReasonCode.CLASS_CALL_CONFIRMED, record.reasonCode)
            assertEquals(listOf(record), sessions.roster(teacher.id, session.id, nativeActor = teacher).records)
            assertEquals(HttpStatusCode.NotFound, get("/attendance/sessions/missing/roster", "teacher").status)
            val superAccount =
                org.companerodeescuela.api.auth.PlatformAccount(
                    "global",
                    "Global QA",
                    "global@example.test",
                    "fixture-hash",
                    setOf(UserRole.SUPER_ADMIN),
                    emailVerifiedAt = f.now.minusSeconds(1),
                )
            f.accounts.create(superAccount)
            assertEquals(session, sessions.roster("global", session.id, true, superAccount.toUserSummary()).session)
            val outsideAdmin =
                superAccount.copy(
                    id = "outside-admin",
                    email = "outside-admin@example.test",
                    roles = setOf(UserRole.ADMIN),
                    institutionId = "tenant-b",
                    adminCapabilities = setOf(AdminCapability.MANAGE_ACADEMICS),
                )
            f.accounts.create(outsideAdmin)
            assertFailsWith<ApiException.Forbidden> { sessions.roster(outsideAdmin.id, session.id, true, outsideAdmin.toUserSummary()) }
            assertEquals(0, groups.listGroupsForUser(student.id).size)
            assertFailsWith<ApiException.Forbidden> { sessions.openSession(student.id, request, student) }
            assertFailsWith<ApiException.Forbidden> { sessions.roster(student.id, session.id, nativeActor = student) }
            assertFailsWith<ApiException.Unauthorized> { projection.requireSession(student.copy(institutionId = "tenant-b"), session) }
            f.sections = emptyList()
            assertEquals(emptyList(), students.discoverActiveFor(student.id, student))
            assertFailsWith<ApiException.Forbidden> { sessions.roster(teacher.id, session.id, nativeActor = teacher) }
            assertEquals(HttpStatusCode.Forbidden, get("/attendance/sessions/${session.id}/roster", "teacher").status)
            Unit
        }
}
