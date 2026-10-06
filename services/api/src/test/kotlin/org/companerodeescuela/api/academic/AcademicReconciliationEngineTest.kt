package org.companerodeescuela.api.academic

import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.AcademicProvenance
import org.companerodeescuela.shared.contracts.BlockStatus
import org.companerodeescuela.shared.contracts.ReconciliationClassification
import org.companerodeescuela.shared.contracts.ScheduleBlock

class AcademicReconciliationEngineTest {
    private val engine = AcademicReconciliationEngine()

    @Test
    fun manualChangeAgainstSchoolDataIsConflict() {
        val remote = block(
            id = "school-1",
            start = "18:00",
            end = "20:00",
            source = AcademicDataSource.SCHOOL_API,
            sourceId = "school-1",
        )
        val local = block(
            id = "manual-1",
            start = "17:00",
            end = "19:00",
            source = AcademicDataSource.ADMIN_MANUAL,
            sourceId = "school-1",
        )

        val result = engine.reconcile(listOf(remote), listOf(local)).single()

        assertEquals(ReconciliationClassification.CONFLICT, result.classification)
    }

    @Test
    fun cachedSchoolRecordChangedByRemoteIsRemoteChanged() {
        val remote = block(
            id = "school-1",
            start = "18:00",
            end = "20:00",
            source = AcademicDataSource.SCHOOL_API,
            sourceId = "school-1",
        )
        val cached = block(
            id = "cached-1",
            start = "17:00",
            end = "19:00",
            source = AcademicDataSource.SCHOOL_API,
            sourceId = "school-1",
        )

        val result = engine.reconcile(listOf(remote), listOf(cached)).single()

        assertEquals(ReconciliationClassification.REMOTE_CHANGED, result.classification)
    }

    @Test
    fun identicalAcademicValuesAreMatchEvenWithDifferentLocalId() {
        val remote = block(
            id = "school-1",
            start = "18:00",
            end = "20:00",
            source = AcademicDataSource.SCHOOL_API,
            sourceId = "school-1",
        )
        val local = block(
            id = "cached-copy",
            start = "18:00",
            end = "20:00",
            source = AcademicDataSource.LOCAL_DRAFT,
            sourceId = "school-1",
        )

        val result = engine.reconcile(listOf(remote), listOf(local)).single()

        assertEquals(ReconciliationClassification.MATCH, result.classification)
    }

    private fun block(
        id: String,
        start: String,
        end: String,
        source: AcademicDataSource,
        sourceId: String?,
    ) = ScheduleBlock(
        id = id,
        dayOfWeek = "MONDAY",
        startTime = start,
        endTime = end,
        subjectId = "PM-9",
        subjectName = "Programación Móvil",
        teacherId = "T-1",
        teacherName = "Docente",
        room = "A-1",
        groupId = "9A",
        groupName = "9A",
        provenance = AcademicProvenance(
            source = source,
            sourceId = sourceId,
            verified = source == AcademicDataSource.SCHOOL_API,
            createdBy = null,
            updatedBy = null,
            createdAtEpochSeconds = 1L,
            updatedAtEpochSeconds = 1L,
        ),
        status = BlockStatus.SCHEDULED,
    )
}
