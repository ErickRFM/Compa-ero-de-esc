package org.companerodeescuela.shared.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Roles a user can hold inside the platform.
 *
 * The enum is intentionally small and stable: it is part of the public contract
 * between the API and the Android client. Business authorization rules are NOT
 * encoded here; see `docs/security/SECURITY_MODEL.md`.
 */
@Serializable
enum class UserRole {
    @SerialName("student")
    STUDENT,

    @SerialName("teacher_pending")
    TEACHER_PENDING,

    @SerialName("teacher")
    TEACHER,

    @SerialName("tutor")
    TUTOR,

    @SerialName("coordinator")
    COORDINATOR,

    @SerialName("admin")
    ADMIN,

    @SerialName("super_admin")
    SUPER_ADMIN,
    ;

    /**
     * True for roles that are expected to be able to see information that
     * belongs to other users (teachers, coordinators, administrative staff).
     * Students are never "staff".
     */
    val isStaff: Boolean
        get() = this == TEACHER || this == TUTOR || this == COORDINATOR || this == ADMIN || this == SUPER_ADMIN

    /**
     * True for roles with administrative reach. Used as a coarse gate before
     * any fine-grained permission check runs.
     */
    val isAdministrative: Boolean
        get() = this == COORDINATOR || this == ADMIN || this == SUPER_ADMIN
}
