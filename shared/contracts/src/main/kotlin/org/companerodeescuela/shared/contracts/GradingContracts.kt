package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GradeCategoryDraft(val name: String, val weightPercent: Double)

@Serializable
data class GradeImportRow(
    val studentId: String,
    val displayName: String? = null,
    val values: Map<String, Double?> = emptyMap(),
)

@Serializable
data class GradeImportPreview(
    val columns: List<String>,
    val rows: List<GradeImportRow>,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class GradeSyncRow(val studentId: String, val finalGrade: Double)

@Serializable
data class GradeSyncRequest(
    val classroomId: String,
    val gradingPeriod: String,
    val rows: List<GradeSyncRow>,
)

/** Capability is verified by the authenticated backend, never guessed in Android. */
@Serializable
data class GradeSyncCapability(val available: Boolean)

@Serializable
enum class GradeSyncStatus {
    @SerialName("synced") SYNCED,
    @SerialName("partially_synced") PARTIALLY_SYNCED,
    @SerialName("unavailable") UNAVAILABLE,
}

@Serializable
data class GradeSyncResult(
    val status: GradeSyncStatus,
    val acceptedStudentIds: List<String> = emptyList(),
    val rejectedStudentIds: List<String> = emptyList(),
    val providerReference: String? = null,
)

@Serializable
enum class GradebookStatus {
    @SerialName("draft") DRAFT,
    @SerialName("published") PUBLISHED,
}

@Serializable
data class GradebookCategory(
    val id: String,
    val name: String,
    val weightPercent: Double,
)

@Serializable
data class GradebookRow(
    val studentId: String,
    val scores: Map<String, Double?>,
)

@Serializable
data class GradebookDraftRequest(
    val classroomId: String,
    val gradingPeriod: String,
    val expectedRevision: Long? = null,
    val categories: List<GradebookCategory>,
    val rows: List<GradebookRow>,
)

@Serializable
data class GradebookRevision(
    val revision: Long,
    val status: GradebookStatus,
    val changedBy: String,
    val changedAtEpochSeconds: Long,
    val categories: List<GradebookCategory> = emptyList(),
    val rows: List<GradebookRow> = emptyList(),
)

@Serializable
data class GradebookSnapshot(
    val classroomId: String,
    val academicPeriodId: String,
    val gradingPeriod: String,
    val teacherId: String,
    val revision: Long,
    val status: GradebookStatus,
    val categories: List<GradebookCategory>,
    val rows: List<GradebookRow>,
    val updatedAtEpochSeconds: Long,
    val publishedAtEpochSeconds: Long? = null,
    val history: List<GradebookRevision> = emptyList(),
)

@Serializable
data class GradebookPublishRequest(
    val classroomId: String,
    val gradingPeriod: String,
    val expectedRevision: Long,
)
