package org.companerodeescuela.feature.attendance

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract

class TeacherAgendaTest {
    private fun occurrence(id: String, date: String = "2026-10-08", end: String = "10:00", status: ClassOccurrenceStatusContract = ClassOccurrenceStatusContract.SCHEDULED) =
        ClassOccurrenceContract(id, null, "course-1", "9A", "MATH", "Matemáticas", "Docente", date, "09:00", end, status)

    @Test
    fun `finished at exact end time and cancelled classes are excluded`() {
        val now = LocalDateTime.parse("2026-10-08T10:00:00")
        val result = upcomingTeacherClasses(listOf(occurrence("ended"), occurrence("current", end = "10:30"),
            occurrence("cancelled", end = "11:00", status = ClassOccurrenceStatusContract.CANCELLED),
            occurrence("tomorrow", date = "2026-10-09"), occurrence("invalid", date = "broken")), now)
        assertEquals(listOf("current", "tomorrow"), result.map { it.id })
    }
}
