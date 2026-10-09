package org.companerodeescuela.api.attendance

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.AcademicOccurrenceProjection
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.academic.mapper.AcademicMappers
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.api.presence.InMemorySchoolPresenceRepository
import org.companerodeescuela.api.presence.SchoolPresencePolicy
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceDisposition
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionRequest
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionStatus
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ClassCallConfirmationRequest
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import org.companerodeescuela.shared.model.ClassOccurrence
import org.companerodeescuela.shared.model.PersonId

class AttendanceServiceTest {
    private val provider = MockAcademicProvider()
    private val initialInstant = Instant.parse("2026-10-05T08:02:00Z")
    private val occurrenceDate = LocalDate.parse("2026-10-05")

    @Test
    fun `expired session cannot be returned as a newly opened pass`() = runTest {
        val clock = MutableClock(initialInstant)
        val service = service(clock = clock)
        val request = requestFor(teacherOccurrence()).copy(durationMinutes = 1)
        service.openSession("T-0001", request)
        clock.advance(Duration.ofMinutes(2))
        assertFailsWith<ApiException.Conflict> { service.openSession("T-0001", request) }
    }

    @Test
    fun `teacher opens attendance only for a dated occurrence they teach`() = runTest {
        val occurrence = teacherOccurrence()
        val service = service()

        val session = service.openSession(
            teacherId = "T-0001",
            request = CreateAttendanceSessionRequest(
                occurrenceId = occurrence.id.value,
                occurrenceDate = occurrence.date.toString(),
            ),
        )

        assertEquals(occurrence.id.value, session.occurrenceId)
        assertEquals("C-9001", session.courseId)
        assertEquals("101-A", session.groupName)
        assertEquals("T-0001", session.openedBy)
    }

    @Test
    fun `same occurrence cannot create duplicate attendance sessions`() = runTest {
        val occurrence = teacherOccurrence()
        var sequence = 0
        val service = service(newId = { "session-" + (++sequence) })
        val request = CreateAttendanceSessionRequest(
            occurrenceId = occurrence.id.value,
            occurrenceDate = occurrence.date.toString(),
        )

        val first = service.openSession("T-0001", request)
        val second = service.openSession("T-0001", request)

        assertEquals("session-1", first.id)
        assertEquals(first, second)
    }

    @Test
    fun `identity enrollment and open session create likely attendance`() = runTest {
        val service = service()
        val session = service.openSession(
            "T-0001",
            requestFor(teacherOccurrence()),
        )

        val record = service.register(
            studentId = "2020-10455",
            sessionId = session.id,
            request = AttendanceAttemptRequest(
                operationId = "op-1",
                deviceTimestampEpochSeconds = initialInstant.epochSecond,
            ),
        )

        assertEquals(AttendanceStatus.LIKELY, record.status)
        assertEquals(AttendanceReasonCode.IDENTITY_SESSION_TIME, record.reasonCode)
        assertEquals(session.occurrenceId, record.occurrenceId)
    }

    @Test
    fun `QR inspection validates evidence without creating attendance`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val qrService = AttendanceQrService(
            secret = "0123456789abcdef0123456789abcdef".toCharArray(),
            repository = repository,
            clock = Clock.fixed(initialInstant, ZoneOffset.UTC),
        )
        val service = service(
            repository = repository,
            qrService = qrService,
            clock = Clock.fixed(initialInstant, ZoneOffset.UTC),
            newId = { "session-inspect" },
        )
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))
        val token = qrService.issue("T-0001", session.id).token

        val result = service.inspectQr(
            studentId = "2020-10455",
            request = AttendanceQrInspectionRequest(
                sessionId = session.id,
                token = token,
            ),
        )

        assertEquals(AttendanceQrInspectionStatus.VALID, result.status)
        assertEquals(session.id, result.session?.id)
        assertEquals(0, repository.recordsForSession(session.id).size)
    }

    @Test
    fun `retry with same operation id is idempotent`() = runTest {
        val service = service()
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))
        val request = AttendanceAttemptRequest(
            operationId = "op-1",
            deviceTimestampEpochSeconds = initialInstant.epochSecond,
        )

        val first = service.register("2020-10455", session.id, request)
        val second = service.register("2020-10455", session.id, request)

        assertEquals(first, second)
    }

    @Test
    fun `second client operation for same student still returns one canonical record`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val service = service(repository = repository)
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))

        val first = service.register(
            "2020-10455",
            session.id,
            AttendanceAttemptRequest("op-1", initialInstant.epochSecond),
        )
        val second = service.register(
            "2020-10455",
            session.id,
            AttendanceAttemptRequest("op-2", initialInstant.epochSecond),
        )

        assertEquals(first.id, second.id)
        assertEquals(1, repository.recordsForSession(session.id).size)
    }

    @Test
    fun `teacher cannot inspect another teacher session without administrative scope`() = runTest {
        val service = service()
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))

        assertFailsWith<ApiException.Forbidden> {
            service.roster(
                actorId = "T-OTHER",
                sessionId = session.id,
                allowCrossOwner = false,
            )
        }
    }

    @Test
    fun `administrative scope can inspect another teacher session`() = runTest {
        val service = service()
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))

        val roster = service.roster(
            actorId = "admin-1",
            sessionId = session.id,
            allowCrossOwner = true,
        )

        assertSame(session, roster.session)
    }

    @Test
    fun `teacher review records reviewer and explicit reason`() = runTest {
        val service = service()
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))
        val record = service.register(
            "2020-10455",
            session.id,
            AttendanceAttemptRequest("op-1", initialInstant.epochSecond),
        )

        val reviewed = service.review(
            reviewerId = "T-0001",
            recordId = record.id,
            request = ReviewAttendanceRequest(
                status = AttendanceStatus.VERIFIED,
                reasonCode = AttendanceReasonCode.TEACHER_REVIEW,
            ),
        )

        assertEquals(AttendanceStatus.VERIFIED, reviewed.status)
        assertEquals("T-0001", reviewed.reviewedBy)
        assertEquals(AttendanceReasonCode.TEACHER_REVIEW, reviewed.reasonCode)
        val wire = Json.encodeToString(reviewed)
        assertTrue(wire.contains("\"originalStatus\":\"" + record.status.name.lowercase() + "\""), wire)
        assertTrue(wire.contains("reviewHistory"), wire)
    }

    @Test
    fun `teacher can mark rejected evidence as present without rewriting evidence`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val service = service(repository = repository)
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))
        val original = service.register(
            "2020-10455",
            session.id,
            AttendanceAttemptRequest("op-1", initialInstant.epochSecond),
        ).copy(
            status = AttendanceStatus.REJECTED,
            reasonCode = AttendanceReasonCode.QR_INVALID,
        )
        repository.replaceRecord(original)

        val reviewed = service.review(
            reviewerId = "T-0001",
            recordId = original.id,
            request = ReviewAttendanceRequest(
                status = AttendanceStatus.VERIFIED,
                disposition = AttendanceDisposition.PRESENT,
            ),
        )

        assertEquals(AttendanceStatus.REJECTED, reviewed.status)
        assertEquals(AttendanceReasonCode.QR_INVALID, reviewed.reasonCode)
        assertEquals(AttendanceDisposition.PRESENT, reviewed.disposition)
        assertEquals("T-0001", reviewed.reviewedBy)
    }

    @Test
    fun `offline attempt captured inside window syncs later as review required`() = runTest {
        val clock = MutableClock(initialInstant)
        val service = service(clock = clock)
        val session = service.openSession(
            "T-0001",
            requestFor(teacherOccurrence()).copy(durationMinutes = 1),
        )
        val capturedAt = initialInstant.plusSeconds(30).epochSecond

        clock.advance(Duration.ofMinutes(2))

        val record = service.register(
            "2020-10455",
            session.id,
            AttendanceAttemptRequest("op-offline", capturedAt),
        )

        assertEquals(AttendanceStatus.REVIEW_REQUIRED, record.status)
        assertEquals(AttendanceReasonCode.OFFLINE_LATE_SYNC, record.reasonCode)
    }

    @Test
    fun `expired server window rejects a late direct attempt`() = runTest {
        val clock = MutableClock(initialInstant)
        val service = service(clock = clock)
        val session = service.openSession(
            "T-0001",
            requestFor(teacherOccurrence()).copy(durationMinutes = 1),
        )

        clock.advance(Duration.ofMinutes(2))

        assertFailsWith<ApiException.Domain> {
            service.register(
                "2020-10455",
                session.id,
                AttendanceAttemptRequest("op-late", clock.instant().epochSecond),
            )
        }
    }

    @Test
    fun `teacher active sessions only include own open windows`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val service = service(repository = repository)
        val own = service.openSession("T-0001", requestFor(teacherOccurrence()))

        repository.createSession(
            AttendanceSessionResponse(
                id = "other-teacher",
                occurrenceId = "other-occurrence",
                courseId = "C-9001",
                groupName = "101-A",
                occurrenceDate = occurrenceDate.toString(),
                scheduledStartsAt = "08:00",
                scheduledEndsAt = "09:00",
                openedBy = "T-OTHER",
                openedAtEpochSeconds = initialInstant.epochSecond,
                closesAtEpochSeconds = initialInstant.plusSeconds(600).epochSecond,
                status = AttendanceSessionStatus.OPEN,
            ),
        )

        assertEquals(listOf(own.id), service.activeForTeacher("T-0001").map { it.id })
    }

    @Test
    fun `class attendance prompt requires active school presence when policy is enabled`() = runTest {
        val presence = SchoolPresenceService(
            repository = InMemorySchoolPresenceRepository(),
            policy = SchoolPresencePolicy(
                entryQrSha256 = sha256("school-entry"),
                allowedSsids = setOf("UD4-Alumno"),
                allowedBssids = emptySet(),
            ),
            clock = Clock.fixed(initialInstant, ZoneOffset.UTC),
            newId = { "presence-1" },
        )
        val service = service(schoolPresenceService = presence)
        val session = service.openSession("T-0001", requestFor(teacherOccurrence()))

        assertFailsWith<ApiException.Forbidden> {
            service.activeFor("2020-10455")
        }

        presence.start(
            studentId = "2020-10455",
            request = StartSchoolPresenceRequest(
                operationId = "presence-op-1",
                qrToken = "school-entry",
                network = SchoolNetworkEvidence(ssid = "UD4-Alumno"),
                deviceTimestampEpochSeconds = initialInstant.epochSecond,
            ),
        )

        assertEquals(listOf(session.id), service.activeFor("2020-10455").map { it.id })
    }

    @Test
    fun `active sessions are filtered by student enrollment`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val service = service(repository = repository)
        val real = service.openSession("T-0001", requestFor(teacherOccurrence()))

        repository.createSession(
            AttendanceSessionResponse(
                id = "other-session",
                occurrenceId = "other-occurrence",
                courseId = "C-NOT-ENROLLED",
                groupName = "404-Z",
                occurrenceDate = occurrenceDate.toString(),
                scheduledStartsAt = "08:00",
                scheduledEndsAt = "09:00",
                openedBy = "T-0001",
                openedAtEpochSeconds = initialInstant.epochSecond,
                closesAtEpochSeconds = initialInstant.plusSeconds(600).epochSecond,
                status = AttendanceSessionStatus.OPEN,
            ),
        )

        assertEquals(listOf(real.id), service.activeFor("2020-10455").map { it.id })
    }


    @Test
    fun `class call requires verified campus entry and fresh school WiFi`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val presence = SchoolPresenceService(
            repository = InMemorySchoolPresenceRepository(),
            policy = SchoolPresencePolicy(
                entryQrSha256 = sha256("school-entry"),
                allowedSsids = setOf("UD4-Alumno"),
                allowedBssids = emptySet(),
            ),
            clock = Clock.fixed(initialInstant, ZoneOffset.UTC),
        )
        val services = service(repository = repository, schoolPresenceService = presence)
        val session = services.openSession("T-0001", requestFor(teacherOccurrence()))
        val request = ClassCallConfirmationRequest("confirm-1", SchoolNetworkEvidence(ssid = "UD4-Alumno"))

        assertFailsWith<ApiException.Forbidden> {
            services.student.confirmClassCall("2020-10455", session.id, request)
        }
        presence.start(
            "2020-10455",
            StartSchoolPresenceRequest(
                "presence-1", "school-entry",
                SchoolNetworkEvidence(ssid = "UD4-Alumno"), initialInstant.epochSecond,
            ),
        )
        assertFailsWith<ApiException.Forbidden> {
            services.student.confirmClassCall(
                "2020-10455", session.id,
                request.copy(schoolNetwork = SchoolNetworkEvidence(ssid = "Home")),
            )
        }
        val confirmed = services.student.confirmClassCall("2020-10455", session.id, request)
        assertEquals(AttendanceStatus.VERIFIED, confirmed.status)
        assertEquals(AttendanceDisposition.PRESENT, confirmed.disposition)
        assertEquals(AttendanceReasonCode.CLASS_CALL_CONFIRMED, confirmed.reasonCode)
        assertEquals(confirmed, services.student.confirmClassCall("2020-10455", session.id, request))
        assertEquals(1, repository.recordsForSession(session.id).size)
    }

    @Test
    fun `class call beyond grace period is late according to server clock`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val presence = SchoolPresenceService(
            repository = InMemorySchoolPresenceRepository(),
            policy = SchoolPresencePolicy(
                entryQrSha256 = sha256("school-entry"),
                allowedSsids = setOf("UD4-Alumno"),
                allowedBssids = emptySet(),
            ),
            clock = Clock.fixed(initialInstant, ZoneOffset.UTC),
        )
        val services = service(repository = repository, schoolPresenceService = presence)
        val session = services.openSession("T-0001", requestFor(teacherOccurrence()).copy(durationMinutes = 15))
        presence.start(
            "2020-10455",
            StartSchoolPresenceRequest(
                "presence-2", "school-entry",
                SchoolNetworkEvidence(ssid = "UD4-Alumno"), initialInstant.epochSecond,
            ),
        )
        val later = AttendanceStudentService(
            repository = repository,
            enrollmentResolver = AttendanceEnrollmentResolver(provider),
            schoolPresenceService = presence,
            clock = Clock.fixed(initialInstant.plusSeconds(360), ZoneOffset.UTC),
        )
        val confirmed = later.confirmClassCall(
            "2020-10455", session.id,
            ClassCallConfirmationRequest("confirm-late", SchoolNetworkEvidence(ssid = "UD4-Alumno")),
        )
        assertEquals(AttendanceDisposition.LATE, confirmed.disposition)
    }

    @Test
    fun `signed classroom QR without WiFi is review only even without a school day`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initialInstant)
        val presence = SchoolPresenceService(
            repository = InMemorySchoolPresenceRepository(),
            policy = SchoolPresencePolicy(
                entryQrSha256 = sha256("school-entry"),
                allowedSsids = setOf("UD4-Alumno"),
                allowedBssids = emptySet(),
            ),
            clock = clock,
        )
        val qr = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock)
        val services = service(repository = repository, clock = clock,
            schoolPresenceService = presence, qrService = qr)
        val session = services.openSession("T-0001", requestFor(teacherOccurrence()))
        val token = qr.issuePack("T-0001", session.id)[1]
        clock.advance(Duration.ofSeconds(40))
        val record = services.register(
            "2020-10455", session.id,
            AttendanceAttemptRequest(
                operationId = "qr-outage-1",
                deviceTimestampEpochSeconds = token.issuedAtEpochSeconds + 1,
                qrToken = token.token,
                schoolNetwork = null,
            ),
        )
        assertEquals(AttendanceStatus.REVIEW_REQUIRED, record.status)
        assertEquals(AttendanceReasonCode.OFFLINE_NETWORK_QR_REVIEW, record.reasonCode)
        assertEquals(null, record.disposition)
        assertEquals(record, services.register(
            "2020-10455", session.id,
            AttendanceAttemptRequest(
                "qr-outage-1", token.issuedAtEpochSeconds + 1, token.token, null,
            ),
        ))
    }

    @Test
    fun `school WiFi without Internet but no campus check-in permits signed QR only for review`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initialInstant)
        val presence = SchoolPresenceService(
            repository = InMemorySchoolPresenceRepository(),
            policy = SchoolPresencePolicy(
                entryQrSha256 = sha256("school-entry"),
                allowedSsids = setOf("UD4-Alumno"),
                allowedBssids = emptySet(),
            ),
            clock = clock,
        )
        val qr = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock)
        val services = service(repository = repository, clock = clock,
            schoolPresenceService = presence, qrService = qr)
        val session = services.openSession("T-0001", requestFor(teacherOccurrence()))
        val signed = qr.issue("T-0001", session.id)
        val result = services.register(
            "2020-10455", session.id,
            AttendanceAttemptRequest(
                "school-wifi-no-internet",
                clock.instant().epochSecond,
                signed.token,
                SchoolNetworkEvidence(ssid = "UD4-Alumno"),
            ),
        )
        assertEquals(AttendanceStatus.REVIEW_REQUIRED, result.status)
        assertEquals(AttendanceReasonCode.OFFLINE_NETWORK_QR_REVIEW, result.reasonCode)
        assertEquals(null, result.disposition)
    }

    @Test
    fun `offline bypass rejects missing or forged classroom QR`() = runTest {
        val repository = InMemoryAttendanceRepository()
        val clock = MutableClock(initialInstant)
        val presence = SchoolPresenceService(
            repository = InMemorySchoolPresenceRepository(),
            policy = SchoolPresencePolicy(
                entryQrSha256 = sha256("school-entry"),
                allowedSsids = setOf("UD4-Alumno"),
                allowedBssids = emptySet(),
            ),
            clock = clock,
        )
        val qr = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock)
        val services = service(repository = repository, clock = clock,
            schoolPresenceService = presence, qrService = qr)
        val session = services.openSession("T-0001", requestFor(teacherOccurrence()))
        assertFailsWith<ApiException.Forbidden> {
            services.register("2020-10455", session.id,
                AttendanceAttemptRequest("empty-offline", clock.instant().epochSecond))
        }
        assertFailsWith<ApiException.Forbidden> {
            services.register("2020-10455", session.id,
                AttendanceAttemptRequest("fake-offline", clock.instant().epochSecond, "not-signed"))
        }
        assertFailsWith<ApiException.Forbidden> {
            services.register("2020-10455", session.id,
                AttendanceAttemptRequest("fake-wifi", clock.instant().epochSecond,
                    qr.issue("T-0001", session.id).token,
                    SchoolNetworkEvidence(ssid = "UD4-Alumno")))
        }
        assertEquals(0, repository.recordsForSession(session.id).size)
    }

    private suspend fun teacherOccurrence(): ClassOccurrence {
        val courses = provider.listCourses().associateBy { it.externalId }
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId("T-0001"),
            slots = provider.getSchedule("T-0001", occurrenceDate),
            coursesById = courses,
        )
        return AcademicOccurrenceProjection.project(schedule, occurrenceDate).first()
    }

    private fun requestFor(occurrence: ClassOccurrence): CreateAttendanceSessionRequest =
        CreateAttendanceSessionRequest(
            occurrenceId = occurrence.id.value,
            occurrenceDate = occurrence.date.toString(),
        )

    private fun service(
        repository: AttendanceRepository = InMemoryAttendanceRepository(),
        clock: Clock = Clock.fixed(initialInstant, ZoneOffset.UTC),
        newId: () -> String = { "session-1" },
        schoolPresenceService: SchoolPresenceService? = null,
        qrService: AttendanceQrService? = null,
    ): TestAttendanceServices {
        val accessPolicy = AttendanceAccessPolicy(repository)
        return TestAttendanceServices(
            session = AttendanceSessionService(
                repository = repository,
                occurrenceResolver = ProviderAttendanceOccurrenceResolver(provider),
                accessPolicy = accessPolicy,
                clock = clock,
                newId = newId,
            ),
            student = AttendanceStudentService(
                repository = repository,
                enrollmentResolver = AttendanceEnrollmentResolver(provider),
                qrService = qrService,
                schoolPresenceService = schoolPresenceService,
                clock = clock,
            ),
            review = AttendanceReviewService(
                repository = repository,
                accessPolicy = accessPolicy,
                clock = clock,
            ),
        )
    }

    private data class TestAttendanceServices(
        val session: AttendanceSessionService,
        val student: AttendanceStudentService,
        val review: AttendanceReviewService,
    ) {
        suspend fun openSession(
            teacherId: String,
            request: CreateAttendanceSessionRequest,
        ) = session.openSession(teacherId, request)

        suspend fun activeForTeacher(teacherId: String) =
            session.activeForTeacher(teacherId)

        suspend fun activeForAdministration() =
            session.activeForAdministration()

        suspend fun activeFor(studentId: String) =
            student.activeFor(studentId)

        suspend fun inspectQr(
            studentId: String,
            request: AttendanceQrInspectionRequest,
        ) = student.inspectQr(studentId, request)

        suspend fun register(
            studentId: String,
            sessionId: String,
            request: AttendanceAttemptRequest,
        ) = student.register(studentId, sessionId, request)

        suspend fun closeSession(
            actorId: String,
            sessionId: String,
            allowCrossOwner: Boolean = false,
        ) = session.closeSession(actorId, sessionId, allowCrossOwner)

        suspend fun roster(
            actorId: String,
            sessionId: String,
            allowCrossOwner: Boolean = false,
        ) = session.roster(actorId, sessionId, allowCrossOwner)

        suspend fun review(
            reviewerId: String,
            recordId: String,
            request: ReviewAttendanceRequest,
            allowCrossOwner: Boolean = false,
        ) = review.review(reviewerId, recordId, request, allowCrossOwner)
    }

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private class MutableClock(
        private var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }
    }
}
