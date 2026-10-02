package org.companerodeescuela.feature.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.CachedAcademicLoad
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.ScheduleEntry

class ScheduleRepositoryTest {
    @Test
    fun `weekly schedule is ordered by weekday then time`() = runTest {
        val repository = ScheduleRepository(
            FakeCache(
                AcademicLoadResponse(
                    student = AcademicProfile("s1", "Ana"),
                    schedule = AcademicScheduleResponse(
                        "s1",
                        listOf(
                            entry("TUESDAY", "09:00"),
                            entry("MONDAY", "11:00"),
                            entry("MONDAY", "08:00"),
                        ),
                    ),
                ),
            ),
        )

        val result = repository.readWeeklySchedule()

        assertEquals(listOf("08:00", "11:00", "09:00"), result.map { it.startsAt })
    }

    private fun entry(day: String, start: String) = ScheduleEntry(
        courseId = day + start,
        subjectCode = "X",
        subjectName = "Materia",
        groupName = "A",
        teacherName = "Docente",
        dayOfWeek = day,
        startsAt = start,
        endsAt = "12:00",
    )

    private class FakeCache(
        private val value: AcademicLoadResponse,
    ) : AcademicSnapshotCache {
        override suspend fun read(): CachedAcademicLoad = CachedAcademicLoad(value, 1)
        override suspend fun write(value: AcademicLoadResponse) = Unit
        override suspend fun clear() = Unit
    }
}
