package org.companerodeescuela.api.academic

import org.companerodeescuela.shared.contracts.AcademicDataSource
import org.companerodeescuela.shared.contracts.AcademicReconciliationItem
import org.companerodeescuela.shared.contracts.ReconciliationClassification
import org.companerodeescuela.shared.contracts.ScheduleBlock

/**
 * Compares the latest institutional schedule with locally stored/manual blocks.
 *
 * A manual record that points at an institutional source via provenance.sourceId
 * is never overwritten automatically when its academic fields diverge.
 */
class AcademicReconciliationEngine {
    fun reconcile(
        remote: List<ScheduleBlock>,
        local: List<ScheduleBlock>,
    ): List<AcademicReconciliationItem> {
        val remoteByKey = remote.associateBy(::identityKey)
        val localByKey = local.associateBy(::identityKey)
        val keys = (remoteByKey.keys + localByKey.keys).toSortedSet()

        return keys.map { key ->
            val remoteBlock = remoteByKey[key]
            val localBlock = localByKey[key]
            when {
                remoteBlock != null && localBlock == null -> AcademicReconciliationItem(
                    key = key,
                    classification = ReconciliationClassification.NEW_REMOTE,
                    remote = remoteBlock,
                )

                remoteBlock == null && localBlock != null -> AcademicReconciliationItem(
                    key = key,
                    classification = ReconciliationClassification.NEW_LOCAL,
                    local = localBlock,
                )

                remoteBlock != null && localBlock != null && sameAcademicValue(remoteBlock, localBlock) ->
                    AcademicReconciliationItem(
                        key = key,
                        classification = ReconciliationClassification.MATCH,
                        remote = remoteBlock,
                        local = localBlock,
                    )

                remoteBlock != null && localBlock != null &&
                    localBlock.provenance.source == AcademicDataSource.SCHOOL_API ->
                    AcademicReconciliationItem(
                        key = key,
                        classification = ReconciliationClassification.REMOTE_CHANGED,
                        remote = remoteBlock,
                        local = localBlock,
                    )

                else -> AcademicReconciliationItem(
                    key = key,
                    classification = ReconciliationClassification.CONFLICT,
                    remote = remoteBlock,
                    local = localBlock,
                )
            }
        }
    }

    private fun identityKey(block: ScheduleBlock): String =
        block.provenance.sourceId?.takeIf(String::isNotBlank)
            ?: block.id

    private fun sameAcademicValue(a: ScheduleBlock, b: ScheduleBlock): Boolean =
        a.dayOfWeek == b.dayOfWeek &&
            a.startTime == b.startTime &&
            a.endTime == b.endTime &&
            a.subjectId == b.subjectId &&
            a.subjectName == b.subjectName &&
            a.teacherId == b.teacherId &&
            a.teacherName == b.teacherName &&
            a.room == b.room &&
            a.groupId == b.groupId &&
            a.groupName == b.groupName &&
            a.status == b.status &&
            a.isContraturno == b.isContraturno
}
