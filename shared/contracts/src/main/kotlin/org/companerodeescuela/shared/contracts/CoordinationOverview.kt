package org.companerodeescuela.shared.contracts

import kotlinx.serialization.Serializable

@Serializable
data class CoordinationOverview(
    val activeGroups: Int,
    val observedAtEpochSeconds: Long,
    val openAttendancePasses: Int? = null,
    val pendingAttendanceReviews: Long? = null,
    val attendanceSessionsSampleLimit: Int? = null,
    val attendanceSampleLimited: Boolean = false,
)
