package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

/**
 * Minimal public representation of an authenticated user.
 *
 * This is the only user shape exposed by the platform for now. It is
 * intentionally small: address books, payroll, contact details and any other
 * sensitive attributes are not part of the contract.
 */
@Serializable
data class UserSummary(
    val id: String,
    val displayName: String,
    val email: String? = null,
    val roles: Set<UserRole> = emptySet(),
    val active: Boolean = true,
    val accountStatus: AccountStatus = if (active) AccountStatus.ACTIVE else AccountStatus.SUSPENDED,
    val authRevision: Long = 0,
    val institutionId: String? = null,
) {
    /**
     * True when the user holds at least one staff role. Convenience helper so
     * clients do not re-implement the same rule.
     */
    val isStaff: Boolean
        get() = roles.any { it.isStaff }
}
