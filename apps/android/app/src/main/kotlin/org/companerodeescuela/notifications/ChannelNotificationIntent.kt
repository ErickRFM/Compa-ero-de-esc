package org.companerodeescuela.notifications

const val CHANNEL_CLICK_ACTION = "org.companerodeescuela.action.OPEN_CHANNEL"

internal data class ChannelNotificationRoute(
    val channelId: String?,
    val openTasks: Boolean,
)

/** The identifier is a navigation hint; the authenticated channel list grants access. */
internal fun channelNotificationRoute(
    action: String?,
    destination: String?,
    type: String?,
    rawChannelId: String?,
): ChannelNotificationRoute? {
    if (!isChannelNotificationIntent(action, destination)) return null
    val channelId =
        rawChannelId?.takeIf {
            it.isNotBlank() && it == it.trim() && it.length <= 128 && it.none(Char::isISOControl)
        }
    val openTasks = isTaskNotificationType(type)
    if (openTasks && channelId == null) return null
    return ChannelNotificationRoute(channelId, openTasks)
}

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
