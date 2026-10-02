package org.companerodeescuela.core.database

import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.ScheduleEntry

class AcademicSnapshotCodecTest {

    @Test
    fun `academic snapshot survives cache serialization`() {
        val value = AcademicLoadResponse(
            student = AcademicProfile(
                id = "student-1",
                displayName = "Ana López",
                email = "ana@example.edu",
            ),
            schedule = AcademicScheduleResponse(
                ownerId = "student-1",
                entries = listOf(
                    ScheduleEntry(
                        courseId = "C-1",
                        subjectCode = "PROG-1",
                        subjectName = "Programación",
                        groupName = "8-A",
                        teacherName = "Mtra. Gómez",
                        dayOfWeek = "MONDAY",
                        startsAt = "08:00",
                        endsAt = "09:00",
                        classroomName = "Aula 4",
                        buildingName = "B-1",
                        campusName = "Campus Central",
                    ),
                ),
            ),
        )

        assertEquals(value, AcademicSnapshotCodec.decode(AcademicSnapshotCodec.encode(value)))
    }
}
