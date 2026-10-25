package org.companerodeescuela.feature.attendance

import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeacherPassOpenPolicyTest {
    @Test
    fun `SIII absolute times honor their instant across device timezones`() {
        val occurrence = classAt(starts = "2026-10-22T15:00:00Z", ends = "2026-10-22T16:00:00Z")
        val zone = java.time.ZoneId.systemDefault()
        val before =
            java.time.Instant
                .parse("2026-10-22T15:59:59Z")
                .atZone(zone)
                .toLocalDateTime()
        val ended =
            java.time.Instant
                .parse("2026-10-22T16:00:00Z")
                .atZone(zone)
                .toLocalDateTime()
        assertEquals(TeacherPassOpenStatus.AVAILABLE, teacherPassOpenStatus(occurrence, before))
        assertEquals(TeacherPassOpenStatus.ENDED, teacherPassOpenStatus(occurrence, ended))
    }

    private fun classAt(
        date: String = "2026-10-08",
        starts: String = "09:00",
        ends: String = "10:30",
        status: ClassOccurrenceStatusContract = ClassOccurrenceStatusContract.SCHEDULED,
    ) = ClassOccurrenceContract(
        id = "class-1",
        patternId = null,
        courseId = "course-1",
        groupName = "9A",
        subjectCode = "MAT",
        subjectName = "Álgebra",
        teacherName = "Docente",
        date = date,
        startsAt = starts,
        endsAt = ends,
        status = status,
    )

    @Test
    fun `session may open while class is still in progress`() {
        assertEquals(
            TeacherPassOpenStatus.AVAILABLE,
            teacherPassOpenStatus(classAt(), LocalDateTime.parse("2026-10-08T10:29:59")),
        )
    }

    @Test
    fun `class ending is exclusive and block is active at the exact minute`() {
        assertEquals(
            TeacherPassOpenStatus.ENDED,
            teacherPassOpenStatus(classAt(), LocalDateTime.parse("2026-10-08T10:30:00")),
        )
        assertEquals(
            TeacherPassOpenStatus.ENDED,
            teacherPassOpenStatus(classAt(), LocalDateTime.parse("2026-10-08T10:33:00")),
        )
    }

    @Test
    fun `cancelled class stays blocked even before its start`() {
        assertEquals(
            TeacherPassOpenStatus.CANCELLED,
            teacherPassOpenStatus(
                classAt(status = ClassOccurrenceStatusContract.CANCELLED),
                LocalDateTime.parse("2026-10-08T08:00:00"),
            ),
        )
    }

    @Test
    fun `invalid times do not offer an operable pass`() {
        val now = LocalDateTime.parse("2026-10-08T09:00:00")
        assertEquals(TeacherPassOpenStatus.INVALID_SCHEDULE, teacherPassOpenStatus(classAt(date = "invalid"), now))
        assertEquals(TeacherPassOpenStatus.INVALID_SCHEDULE, teacherPassOpenStatus(classAt(starts = "bad"), now))
        assertEquals(TeacherPassOpenStatus.INVALID_SCHEDULE, teacherPassOpenStatus(classAt(starts = "10:00", ends = "10:00"), now))
    }

    @Test
    fun `overnight occurrence uses next day as closing time`() {
        val overnight = classAt(starts = "23:00", ends = "01:00")
        assertEquals(
            TeacherPassOpenStatus.AVAILABLE,
            teacherPassOpenStatus(overnight, LocalDateTime.parse("2026-10-09T00:59:59")),
        )
        assertEquals(
            TeacherPassOpenStatus.ENDED,
            teacherPassOpenStatus(overnight, LocalDateTime.parse("2026-10-09T01:00:00")),
        )
    }

    @Test
    fun `HTTP conflict explains opening context without leaking technical detail`() {
        val message = teacherPassOpenError(AppError.Http(409, technicalDetail = "secret"))
        assertTrue(message.contains("sesión cerrada"))
        assertTrue(!message.contains("secret"))
        assertEquals(
            AppError.Http(503).userMessage,
            teacherPassOpenError(AppError.Http(503)),
        )
    }
}
