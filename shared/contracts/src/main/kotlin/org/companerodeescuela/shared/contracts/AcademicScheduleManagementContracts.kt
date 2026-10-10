package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

/**
 * Staff-authored schedule mutation. The server owns provenance fields and
 * derives source from the authenticated role; clients cannot forge them.
 */
@Serializable
data class UpsertScheduleBlockRequest(
    val id: String? = null,
    val ownerId: String,
    val sourceId: String? = null,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val subjectId: String? = null,
    val subjectName: String,
    val teacherId: String? = null,
    val teacherName: String? = null,
    val room: String? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val status: BlockStatus = BlockStatus.SCHEDULED,
    val isContraturno: Boolean = false,
    val recurrence: ScheduleRecurrence = ScheduleRecurrence.WEEKLY,
    val seriesId: String? = null,
    val effectiveDate: String? = null,
    val mutationScope: ScheduleMutationScope = ScheduleMutationScope.ENTIRE_SERIES,
    val reason: String? = null,
    val operationId:String?=null,
    val expectedRevision:Long=0,
    val groupScopeRevision:Long=0,
    val proof:String?=null,
) { override fun toString()="UpsertScheduleBlockRequest(ownerId=$ownerId, groupId=$groupId, proof=[redacted])" }

@Serializable
data class ManagedScheduleBlock(
    val ownerId: String,
    val block: ScheduleBlock,
    val reason: String? = null,
    val institutionId:String?=null,
    val groupScopeRevision:Long=-1,
    val revision:Long=0,
    val operationId:String?=null,
    val operationDigest:String?=null,
)

@Serializable
data class ResolveScheduleConflictRequest(
    val resolution: ReconciliationResolution,
    val reason: String? = null,
)

@Serializable
data class ScheduleConflictResolution(
    val key: String,
    val ownerId: String,
    val resolution: ReconciliationResolution,
    val resolvedBy: String,
    val resolvedAtEpochSeconds: Long,
    val reason: String? = null,
)

@Serializable data class AcademicOperationRequest(val operationId:String,val expectedRevision:Long,val proof:String) {
    override fun toString()="AcademicOperationRequest(operationId=$operationId, proof=[redacted])"
}
