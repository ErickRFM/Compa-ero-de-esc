package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Identifies where an academic record came from.
 *
 * Source is provenance, not precedence: a manual record must never silently
 * overwrite an institutional record (or vice versa). Reconciliation decides
 * how conflicting records are presented and resolved.
 */
@Serializable
enum class AcademicDataSource {
    @SerialName("school_api") SCHOOL_API,
    @SerialName("admin_manual") ADMIN_MANUAL,
    @SerialName("supervisor_manual") SUPERVISOR_MANUAL,
    @SerialName("user_import") USER_IMPORT,
    @SerialName("user_manual") USER_MANUAL,
    @SerialName("local_draft") LOCAL_DRAFT,
}

@Serializable
data class AcademicProvenance(
    val source: AcademicDataSource,
    val sourceId: String? = null,
    val verified: Boolean = false,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val createdAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
)
