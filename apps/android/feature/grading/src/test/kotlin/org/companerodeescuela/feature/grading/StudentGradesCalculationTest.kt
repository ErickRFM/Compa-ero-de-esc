package org.companerodeescuela.feature.grading

import org.companerodeescuela.shared.contracts.GradebookCategory
import org.companerodeescuela.shared.contracts.GradebookSnapshot
import org.companerodeescuela.shared.contracts.GradebookStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StudentGradesCalculationTest {
    @Test
    fun `weighted average uses persisted category ids`() {
        val snapshot =
            GradebookSnapshot(
                classroomId = "class-a",
                academicPeriodId = "period-1",
                gradingPeriod = "Parcial 1",
                teacherId = "teacher-a",
                revision = 2,
                status = GradebookStatus.PUBLISHED,
                categories =
                    listOf(
                        GradebookCategory("project", "Proyecto", 40.0),
                        GradebookCategory("exam", "Examen", 60.0),
                    ),
                rows = emptyList(),
                updatedAtEpochSeconds = 10,
            )

        assertEquals(8.6, weightedAverage(snapshot, mapOf("project" to 8.0, "exam" to 9.0)))
        assertNull(weightedAverage(snapshot, mapOf("project" to 8.0)))
    }
}
