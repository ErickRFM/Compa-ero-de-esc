package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReconciliationClassification {
    @SerialName("match") MATCH,
    @SerialName("new_remote") NEW_REMOTE,
    @SerialName("new_local") NEW_LOCAL,
    @SerialName("remote_changed") REMOTE_CHANGED,
    @SerialName("local_override") LOCAL_OVERRIDE,
    @SerialName("conflict") CONFLICT,
}

@Serializable
enum class ReconciliationResolution {
    @SerialName("use_school") USE_SCHOOL,
    @SerialName("keep_manual") KEEP_MANUAL,
    @SerialName("review") REVIEW,
}

@Serializable
data class AcademicReconciliationItem(
    val key: String,
    val classification: ReconciliationClassification,
    val remote: ScheduleBlock? = null,
    val local: ScheduleBlock? = null,
    val resolution: ReconciliationResolution? = null,
    val resolvedBy: String? = null,
    val resolvedAtEpochSeconds: Long? = null,
    val reason: String? = null,
)
