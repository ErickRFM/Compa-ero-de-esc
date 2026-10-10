package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

/** Account lifecycle is independent from granted roles and institutional assignments. */
@Serializable
enum class AccountStatus {
    PENDING_VERIFICATION, PENDING_APPROVAL, ACTIVE, SUSPENDED, REVOKED;

    val permitsSession: Boolean
        get() = this != SUSPENDED && this != REVOKED
}
