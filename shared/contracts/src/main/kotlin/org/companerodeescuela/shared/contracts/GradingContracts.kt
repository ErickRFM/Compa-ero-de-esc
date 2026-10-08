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
