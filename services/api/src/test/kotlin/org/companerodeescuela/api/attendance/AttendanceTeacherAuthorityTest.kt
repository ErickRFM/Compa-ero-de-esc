package org.companerodeescuela.api.attendance

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.academic.AcademicOccurrenceProjection
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.academic.dto.ExternalScheduleSlot
import org.companerodeescuela.api.integrations.academic.mapper.AcademicMappers
import org.companerodeescuela.api.integrations.mock.MockAcademicProvider
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceReasonCode
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceStatus
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import org.companerodeescuela.shared.model.PersonId

class AttendanceTeacherAuthorityTest {
    @Test fun `revoked teacher assignment removes a previously opened active session`() = runTest {
        val fixture = Fixture()
        val session = fixture.open()
        assertEquals(listOf(session.id), fixture.service.activeForTeacher("T-0001").map { it.id })
        fixture.provider.revoked = true
        assertEquals(emptyList(), fixture.service.activeForTeacher("T-0001"))
    }

    @Test fun `revoked teacher cannot retrieve the old session roster`() = runTest {
        val fixture = Fixture()
        val session = fixture.open()
        assertEquals(session.id, fixture.service.roster("T-0001", session.id).session.id)
        fixture.provider.revoked = true
        assertFailsWith<ApiException.Forbidden> { fixture.service.roster("T-0001", session.id) }
    }

    @Test fun `revoked teacher cannot close the old session`() = runTest {
        val fixture = Fixture()
        val session = fixture.open()
        fixture.provider.revoked = true
        assertFailsWith<ApiException.Forbidden> { fixture.service.closeSession("T-0001", session.id) }
        assertEquals(AttendanceSessionStatus.OPEN, fixture.repository.findSession(session.id)!!.status)
    }

    @Test fun `academic outage fails closed instead of trusting old session ownership`() = runTest {
        val fixture = Fixture()
        val session = fixture.open()
        fixture.provider.unavailable = true
        assertFailsWith<ApiException.DependencyUnavailable> { fixture.service.roster("T-0001", session.id) }
        assertFailsWith<ApiException.DependencyUnavailable> { fixture.service.activeForTeacher("T-0001") }
    }

    @Test fun `explicit administrative scope retains access during teacher revocation`() = runTest {
        val fixture = Fixture()
        val session = fixture.open()
        fixture.provider.revoked = true
        assertEquals(session.id, fixture.service.roster("admin", session.id, allowCrossOwner = true).session.id)
    }

    @Test fun `revoked teacher cannot change a prior human review`() = runTest {
        val fixture = Fixture()
        val session = fixture.open()
        val record = AttendanceRecordResponse("record", "op", session.id, session.occurrenceId, "student",
            AttendanceStatus.LIKELY, AttendanceReasonCode.IDENTITY_SESSION_TIME, 1, 1)
        fixture.repository.writeAttempt(record)
        fixture.review.review("T-0001", record.id, ReviewAttendanceRequest(AttendanceStatus.VERIFIED))
        val original = fixture.repository.findRecord(record.id)
        fixture.provider.revoked = true
        assertFailsWith<ApiException.Forbidden> {
            fixture.review.review("T-0001", record.id, ReviewAttendanceRequest(AttendanceStatus.REJECTED))
        }
        assertEquals(original, fixture.repository.findRecord(record.id))
    }

    @Test fun `revoked teacher cannot mint QR for an old open session`() = runTest {
        val fixture = Fixture()
        val session = fixture.open()
        fixture.qr.issue("T-0001", session.id)
        fixture.provider.revoked = true
        assertFailsWith<ApiException.Forbidden> { fixture.qr.issue("T-0001", session.id) }
    }

    @Test fun `stored session course must match the current authorized occurrence`() = runTest {
        val fixture = Fixture()
        val session = fixture.open()
        fixture.repository.replaceSession(session.copy(courseId = "another-course"))
        assertFailsWith<ApiException.Forbidden> { fixture.service.roster("T-0001", session.id) }
    }

    private class Fixture {
        val repository = InMemoryAttendanceRepository()
        val provider = MutableAcademicProvider()
        private val clock = Clock.fixed(Instant.parse("2026-10-05T08:02:00Z"), ZoneOffset.UTC)
        private val resolver = ProviderAttendanceOccurrenceResolver(provider)
        private val policy = AttendanceAccessPolicy(repository, resolver)
        val service = AttendanceSessionService(repository, resolver, policy, clock)
        val review = AttendanceReviewService(repository, policy, clock)
        val qr = AttendanceQrService("q".repeat(48).toCharArray(), repository, clock, accessPolicy = policy)

        suspend fun open(): org.companerodeescuela.shared.contracts.AttendanceSessionResponse {
            val date = LocalDate.parse("2026-10-05")
            val courses = provider.listCourses().associateBy { it.externalId }
            val schedule = AcademicMappers.toSchedule(PersonId("T-0001"), provider.getSchedule("T-0001", date), courses)
            val occurrence = AcademicOccurrenceProjection.project(schedule, date).first()
            return service.openSession("T-0001", CreateAttendanceSessionRequest(occurrence.id.value, occurrence.date.toString()))
        }
    }

    private class MutableAcademicProvider(private val delegate: AcademicProvider = MockAcademicProvider()) : AcademicProvider by delegate {
        var revoked = false
        var unavailable = false
        override suspend fun getSchedule(externalId: String, weekOf: LocalDate?): List<ExternalScheduleSlot> {
            if (unavailable) throw IntegrationException(id, IntegrationException.Category.UNAVAILABLE, "QA unavailable")
            return if (revoked) emptyList() else delegate.getSchedule(externalId, weekOf)
        }
    }
}
