package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class AcademicGroupSummary(
    val id: String,
    val name: String,
    val active: Boolean = true,
    val institutionId: String? = null,
    val periodId: String? = null,
    val startsAtEpochSeconds: Long? = null,
    val endsAtEpochSeconds: Long? = null,
    val scopeRevision: Long = 0,
)

@Serializable
data class CreateAcademicGroupRequest(
    val id: String,
    val name: String,
    val institutionId:String?=null,
    val periodId:String?=null,
    val startsAtEpochSeconds:Long?=null,
    val endsAtEpochSeconds:Long?=null,
    val operationId:String?=null,
    val proof:String?=null,
) { override fun toString()="CreateAcademicGroupRequest(id=$id, institutionId=$institutionId, proof=[redacted])" }

@Serializable
data class AssignAcademicGroupMemberRequest(
    val userId: String,
    val operationId:String?=null,
    val expectedScopeRevision:Long=0,
    val proof:String?=null,
) { override fun toString()="AssignAcademicGroupMemberRequest(userId=$userId, proof=[redacted])" }

@Serializable
data class AcademicGroupMembership(
    val groupId: String,
    val groupName: String,
    val userId: String,
    val joinedAtEpochSeconds: Long,
)

@Serializable
data class AcademicGroupScopeRequest(val operationId:String,val expectedScopeRevision:Long,
    val institutionId:String,val periodId:String,val startsAtEpochSeconds:Long,val endsAtEpochSeconds:Long,val proof:String) {
    override fun toString()="AcademicGroupScopeRequest(operationId=$operationId, periodId=$periodId, proof=[redacted])"
}
