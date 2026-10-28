package org.companerodeescuela.api.attendance

import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionStatus
import org.companerodeescuela.shared.contracts.AttendanceStatus

const val MAX_INSTITUTION_ATTENDANCE_SESSIONS = 1_000

/** Aggregate evidence only; a pending confirmation never certifies physical presence. */
data class AttendanceInstitutionOverview(
    val openPasses: Int,
    val pendingReviews: Long,
    val sessionSampleLimit: Int,
    val sampleLimited: Boolean,
)

internal fun AttendanceSessionResponse.belongsToInstitutionOverview(institution: String): Boolean =
    identitySource == AttendanceIdentitySource.NATIVE &&
        institutionId == institution &&
        !groupId.isNullOrBlank() &&
        groupScopeRevision >= 0

internal fun AttendanceSessionResponse.isOpenAt(at: Long): Boolean =
    status == AttendanceSessionStatus.OPEN &&
        closedAtEpochSeconds == null &&
        openedAtEpochSeconds <= at &&
        closesAtEpochSeconds > at

internal fun AttendanceRecordResponse.isPendingInstitutionalReview(): Boolean =
    withCurrentPresencePolicy().let { it.status == AttendanceStatus.REVIEW_REQUIRED && it.disposition == null }
