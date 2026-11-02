package org.companerodeescuela.notifications

const val CHANNEL_CLICK_ACTION = "org.companerodeescuela.action.OPEN_CHANNEL"

/** Distinguish classroom task notices from generic channel updates. */
internal fun isTaskNotificationType(type: String?): Boolean =
    type in setOf("channel_task", "channel_task_review", "channel_task_submission")

/**
 * Validates notification intent for institutional channel notices before
 * triggering top-level channel navigation.
 */
internal fun isChannelNotificationIntent(
    action: String?,
    rawDestination: String?,
): Boolean {
    if (action == CHANNEL_CLICK_ACTION) return true
    if (rawDestination.equals("channel", ignoreCase = true)) return true
    return false
}
