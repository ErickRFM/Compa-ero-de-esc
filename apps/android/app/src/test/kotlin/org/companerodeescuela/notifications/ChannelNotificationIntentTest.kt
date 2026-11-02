package org.companerodeescuela.notifications

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelNotificationIntentTest {
    @Test
    fun `recognizes task publication submission and review only`() {
        assertTrue(isTaskNotificationType("channel_task"))
        assertTrue(isTaskNotificationType("channel_task_submission"))
        assertTrue(isTaskNotificationType("channel_task_review"))
        assertFalse(isTaskNotificationType("channel"))
        assertFalse(isTaskNotificationType("attendance_session_opened"))
        assertFalse(isTaskNotificationType(null))
    }

    @Test
    fun `validates explicit channel action or destination extra`() {
        assertTrue(isChannelNotificationIntent(CHANNEL_CLICK_ACTION, null))
        assertTrue(isChannelNotificationIntent(null, "channel"))
        assertTrue(isChannelNotificationIntent("android.intent.action.VIEW", "CHANNEL"))

        assertFalse(isChannelNotificationIntent("android.intent.action.MAIN", null))
        assertFalse(isChannelNotificationIntent(null, "attendance"))
        assertFalse(isChannelNotificationIntent(null, null))
    }
}
