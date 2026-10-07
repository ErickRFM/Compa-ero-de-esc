package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExcuseStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("under_review")
    UNDER_REVIEW,

    @SerialName("approved")
    APPROVED,

    @SerialName("rejected")
    REJECTED,

    @SerialName("cancelled")
    CANCELLED,
}

@Serializable
data class ExcuseRequestSummary(
    val id: String,
    val studentId: String,
    val academicGroupId: String,
    val attendanceDateIso: String,
    val reason: String,
    val attachmentRefs: List<String> = emptyList(),
    val status: ExcuseStatus,
    val submittedAtEpochSeconds: Long,
    val reviewedAtEpochSeconds: Long? = null,
    val reviewedBy: String? = null,
    val reviewComment: String? = null,
)

@Serializable
data class SubmitExcuseRequest(
    val academicGroupId: String,
    val attendanceDateIso: String,
    val reason: String,
    val attachmentRefs: List<String> = emptyList(),
)

@Serializable
data class ReviewExcuseRequest(
    val status: ExcuseStatus,
    val comment: String? = null,
)
