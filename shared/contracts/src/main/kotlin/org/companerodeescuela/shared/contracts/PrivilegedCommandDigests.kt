package org.companerodeescuela.shared.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

/** Shared canonical encoding; services always recompute it from their actual path and typed body. */
object PrivilegedCommandDigests {
    private val json = Json { encodeDefaults = true }

    fun staffApproval(
        targetId: String,
        request: ApproveStaffRequest,
    ): String = digest("staff-approval-v1", targetId, json.encodeToJsonElement(request.copy(proof = "")))

    fun accountLifecycle(
        targetId: String,
        request: AccountLifecycleRequest,
    ): String = digest("account-lifecycle-v1", targetId, json.encodeToJsonElement(request.copy(proof = "")))

    fun tutorAssignment(
        groupId: String,
        request: CreateTutorAssignmentRequest,
    ): String = digest("tutor-assignment-v1", groupId, json.encodeToJsonElement(request.copy(proof = null)))

    fun tutorRevocation(
        assignmentId: String,
        request: RevokeTutorAssignmentRequest,
    ): String = digest("tutor-revocation-v1", assignmentId, json.encodeToJsonElement(request.copy(proof = "")))

    fun groupScope(
        groupId: String,
        request: AcademicGroupScopeRequest,
    ): String = digest("group-scope-v1", groupId, json.encodeToJsonElement(request.copy(proof = "")))

    fun groupCreation(
        groupId: String,
        request: CreateAcademicGroupRequest,
    ): String = digest("group-create-v1", groupId, json.encodeToJsonElement(request.copy(proof = null)))

    fun groupMembership(
        groupId: String,
        request: AssignAcademicGroupMemberRequest,
    ): String = digest("group-member-v1", groupId, json.encodeToJsonElement(request.copy(proof = null)))

    fun classroomAssignment(
        groupId: String,
        request: CreateClassroomRequest,
    ): String = digest("classroom-assignment-v1", groupId, json.encodeToJsonElement(request.copy(proof = null)))

    fun scheduleUpsert(
        ownerId: String,
        request: UpsertScheduleBlockRequest,
    ): String = digest("schedule-upsert-v1", ownerId, json.encodeToJsonElement(request.copy(proof = null)))

    fun scheduleCancellation(
        id: String,
        request: AcademicOperationRequest,
    ): String = digest("schedule-cancel-v1", id, json.encodeToJsonElement(request.copy(proof = "")))

    fun schoolQrCreation(
        institutionId: String,
        request: CreateSchoolEntryQrRequest,
    ): String = digest("school-qr-create-v1", institutionId, json.encodeToJsonElement(request.copy(proof = null)))

    fun schoolQrRevocation(
        id: String,
        request: AcademicOperationRequest,
    ): String = digest("school-qr-revoke-v1", id, json.encodeToJsonElement(request.copy(proof = "")))

    fun schoolQrRegeneration(
        id: String,
        request: AcademicOperationRequest,
    ): String = digest("school-qr-regenerate-v1", id, json.encodeToJsonElement(request.copy(proof = "")))

    fun eventWrite(
        id: String,
        request: AcademicEventWriteRequest,
    ): String = digest("academic-event-write-v1", id, json.encodeToJsonElement(request.copy(proof = null)))

    fun eventCancellation(
        id: String,
        request: AcademicOperationRequest,
    ): String = digest("academic-event-cancel-v1", id, json.encodeToJsonElement(request.copy(proof = "")))

    private fun digest(
        kind: String,
        targetId: String,
        payload: JsonElement,
    ): String {
        val envelope =
            buildJsonObject {
                put("kind", kind)
                put("targetId", targetId)
                put("command", payload)
            }
        return MessageDigest.getInstance("SHA-256").digest(json.encodeToString(envelope).toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02x".format(
                it.toInt() and 255,
            )
        }
    }
}
