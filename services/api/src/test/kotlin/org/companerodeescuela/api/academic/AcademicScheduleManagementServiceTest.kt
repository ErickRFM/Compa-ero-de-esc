package org.companerodeescuela.api.academic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.BlockStatus
import org.companerodeescuela.shared.contracts.ScheduleShift
import org.companerodeescuela.shared.contracts.UpsertScheduleBlockRequest

class AcademicScheduleManagementServiceTest {
    private val repository = InMemoryAcademicScheduleOverrideRepository()
    private val clock = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC)
    private val service = AcademicScheduleManagementService(
        repository = repository,
        clock = clock,
        newId = { "SCH-TEST" },
    )

    @Test
    fun adminCreatesTraceableAfternoonBlock() = runTest {
        val result = service.upsert(
            actorId = "QA-ADMIN",
            source = AcademicDataSource.ADMIN_MANUAL,
            request = request(),
        )

        assertEquals("SCH-TEST", result.block.id)
        assertEquals(ScheduleShift.AFTERNOON, result.block.shift)
        assertEquals(AcademicDataSource.ADMIN_MANUAL, result.block.provenance.source)
        assertEquals("QA-ADMIN", result.block.provenance.createdBy)
        assertEquals("QA-ADMIN", result.block.provenance.updatedBy)
        assertTrue(result.block.provenance.verified)
        assertEquals("Prueba de contraturno", result.reason)
    }

    @Test
    fun coordinatorSourceIsPreserved() = runTest {
        val result = service.upsert(
            actorId = "QA-SUPERVISOR",
            source = AcademicDataSource.SUPERVISOR_MANUAL,
            request = request(isContraturno = true),
        )

        assertEquals(AcademicDataSource.SUPERVISOR_MANUAL, result.block.provenance.source)
        assertTrue(result.block.isContraturno)
    }

    @Test
    fun rejectsBlocksOutsideAcademicDay() = runTest {
        assertFailsWith<ApiException.Validation> {
            service.upsert(
                actorId = "QA-ADMIN",
                source = AcademicDataSource.ADMIN_MANUAL,
                request = request(start = "20:00", end = "21:00"),
            )
        }
    }

    @Test
    fun rejectsNonStaffProvenance() = runTest {
        assertFailsWith<ApiException.Validation> {
            service.upsert(
                actorId = "QA-STUDENT",
                source = AcademicDataSource.USER_MANUAL,
                request = request(),
            )
        }
    }

    private fun request(
        start: String = "18:00",
        end: String = "20:00",
        isContraturno: Boolean = false,
    ) = UpsertScheduleBlockRequest(
        ownerId = "QA-STUDENT",
        sourceId = "school-slot-1",
        dayOfWeek = "MONDAY",
        startTime = start,
        endTime = end,
        subjectId = "PM-9",
        subjectName = "Programación Móvil",
        teacherId = "QA-TEACHER",
        teacherName = "QA Docente",
        room = "A-1",
        groupId = "9A",
        groupName = "9A",
        status = BlockStatus.SCHEDULED,
        isContraturno = isContraturno,
        reason = "Prueba de contraturno",
    )
}
