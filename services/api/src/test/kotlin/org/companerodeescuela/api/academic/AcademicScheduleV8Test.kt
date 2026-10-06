package org.companerodeescuela.api.academic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.ScheduleRecurrence
import org.companerodeescuela.shared.contracts.UpsertScheduleBlockRequest

class AcademicScheduleV8Test {
    private val service = AcademicScheduleManagementService(
        repository = InMemoryAcademicScheduleOverrideRepository(),
        clock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC),
        newId = { "SCH-V8" },
    )

    @Test
    fun `weekly block receives a stable series id`() = runTest {
        val saved = service.upsert(
            actorId = "admin-1",
            source = AcademicDataSource.ADMIN_MANUAL,
            request = request(),
        )

        assertEquals(ScheduleRecurrence.WEEKLY, saved.block.recurrence)
        assertEquals(saved.block.id, saved.block.seriesId)
    }

    @Test
    fun `one time block requires effective date`() = runTest {
        assertFailsWith<ApiException.Validation> {
            service.upsert(
                actorId = "admin-1",
                source = AcademicDataSource.ADMIN_MANUAL,
                request = request().copy(
                    recurrence = ScheduleRecurrence.ONE_TIME,
                    effectiveDate = null,
                ),
            )
        }
    }

    @Test
    fun `one time block stores effective date`() = runTest {
        val saved = service.upsert(
            actorId = "admin-1",
            source = AcademicDataSource.ADMIN_MANUAL,
            request = request().copy(
                recurrence = ScheduleRecurrence.ONE_TIME,
                effectiveDate = "2026-10-12",
            ),
        )

        assertEquals("2026-10-12", saved.block.effectiveDate)
    }

    @Test
    fun `overlapping managed blocks are rejected`() = runTest {
        service.upsert(
            actorId = "admin-1",
            source = AcademicDataSource.ADMIN_MANUAL,
            request = request().copy(id = "A"),
        )

        assertFailsWith<ApiException.Conflict> {
            service.upsert(
                actorId = "admin-1",
                source = AcademicDataSource.ADMIN_MANUAL,
                request = request().copy(
                    id = "B",
                    startTime = "09:30",
                    endTime = "10:30",
                    subjectName = "Base de Datos",
                ),
            )
        }
    }

    @Test
    fun `adjacent managed blocks are allowed`() = runTest {
        service.upsert(
            actorId = "admin-1",
            source = AcademicDataSource.ADMIN_MANUAL,
            request = request().copy(id = "A"),
        )

        val saved = service.upsert(
            actorId = "admin-1",
            source = AcademicDataSource.ADMIN_MANUAL,
            request = request().copy(
                id = "B",
                startTime = "10:00",
                endTime = "11:00",
                subjectName = "Base de Datos",
            ),
        )

        assertEquals("10:00", saved.block.startTime)
    }

    private fun request() = UpsertScheduleBlockRequest(
        ownerId = "student-1",
        dayOfWeek = "MONDAY",
        startTime = "09:00",
        endTime = "10:00",
        subjectName = "Sistemas Embebidos",
    )
}
