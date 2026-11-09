package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class MaticaProgressSummary(
    val totalXp: Int,
    val weeklyXp: Int,
    val level: Int,
    val streak: Int,
    val longestStreak: Int,
    val lastActiveDate: String? = null,
)

@Serializable
data class MaticaProgressLinkRequest(val code: String)
