package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class CoordinationOverview(
    val activeGroups: Int,
    val observedAtEpochSeconds: Long,
)
