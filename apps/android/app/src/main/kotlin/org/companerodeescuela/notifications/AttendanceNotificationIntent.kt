package org.companerodeescuela.notifications

import org.companerodeescuela.shared.contracts.AttendancePushContract
import java.util.UUID

/** Validates the notification deep link before it is passed to authenticated attendance UI. */
internal fun parseAttendanceSessionId(
    action: String?,
    rawSessionId: String?,
): String? {
    if (action != AttendancePushContract.CLICK_ACTION || rawSessionId == null) return null
    val canonical = runCatching { UUID.fromString(rawSessionId).toString() }.getOrNull() ?: return null
    return canonical.takeIf { it.equals(rawSessionId, ignoreCase = true) }
}
