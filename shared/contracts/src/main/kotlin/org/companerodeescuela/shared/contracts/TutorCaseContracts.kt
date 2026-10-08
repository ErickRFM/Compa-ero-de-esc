package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TutorCaseStatus {
    @SerialName("open") OPEN,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("referred") REFERRED,
    @SerialName("closed") CLOSED,
}

@Serializable
enum class TutorNoteVisibility {
    @SerialName("student_visible") STUDENT_VISIBLE,
    @SerialName("tutor_internal") TUTOR_INTERNAL,
    @SerialName("coordination_restricted") COORDINATION_RESTRICTED,
    @SerialName("sensitive_restricted") SENSITIVE_RESTRICTED,
}

@Serializable
data class CreateTutorCaseRequest(
    val academicGroupId: String,
    val studentId: String,
    val summary: String,
)

@Serializable
data class AddTutorCaseNoteRequest(
    val body: String,
    val visibility: TutorNoteVisibility = TutorNoteVisibility.TUTOR_INTERNAL,
)

@Serializable
data class TutorCaseNoteSummary(
    val id: String,
    val body: String,
    val visibility: TutorNoteVisibility,
    val authorId: String,
    val createdAtEpochSeconds: Long,
)

@Serializable
data class TutorCaseSummary(
    val id: String,
    val academicGroupId: String,
    val studentId: String,
    val summary: String,
    val status: TutorCaseStatus,
    val createdBy: String,
    val createdAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
    val notes: List<TutorCaseNoteSummary> = emptyList(),
    val version: Long = 0L,
)

@Serializable
data class StudentTutorNoteSummary(
    val id: String,
    val academicGroupId: String,
    val body: String,
    val authorId: String,
    val createdAtEpochSeconds: Long,
)
