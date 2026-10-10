package org.companerodeescuela.shared.contracts

import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Shared canonical encoding; services always recompute it from their actual path and typed body. */
object PrivilegedCommandDigests {
    private val json=Json { encodeDefaults=true }
    fun staffApproval(targetId:String,request:ApproveStaffRequest):String = digest("staff-approval-v1",targetId,json.encodeToJsonElement(request.copy(proof="")))
    fun accountLifecycle(targetId:String,request:AccountLifecycleRequest):String = digest("account-lifecycle-v1",targetId,json.encodeToJsonElement(request.copy(proof="")))
    fun tutorAssignment(groupId:String,request:CreateTutorAssignmentRequest):String = digest("tutor-assignment-v1",groupId,json.encodeToJsonElement(request.copy(proof=null)))
    fun tutorRevocation(assignmentId:String,request:RevokeTutorAssignmentRequest):String = digest("tutor-revocation-v1",assignmentId,json.encodeToJsonElement(request.copy(proof="")))
    fun groupScope(groupId:String,request:AcademicGroupScopeRequest):String = digest("group-scope-v1",groupId,json.encodeToJsonElement(request.copy(proof="")))
    fun groupCreation(groupId:String,request:CreateAcademicGroupRequest):String = digest("group-create-v1",groupId,json.encodeToJsonElement(request.copy(proof=null)))
    fun groupMembership(groupId:String,request:AssignAcademicGroupMemberRequest):String = digest("group-member-v1",groupId,json.encodeToJsonElement(request.copy(proof=null)))
    fun classroomAssignment(groupId:String,request:CreateClassroomRequest):String = digest("classroom-assignment-v1",groupId,json.encodeToJsonElement(request.copy(proof=null)))
    private fun digest(kind:String,targetId:String,payload:JsonElement):String {
        val envelope=buildJsonObject { put("kind",kind);put("targetId",targetId);put("command",payload) }
        return MessageDigest.getInstance("SHA-256").digest(json.encodeToString(envelope).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
