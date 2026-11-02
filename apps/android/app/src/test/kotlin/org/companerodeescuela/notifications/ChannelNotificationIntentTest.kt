package org.companerodeescuela.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChannelNotificationIntentTest {
    @Test
    fun `cold and warm task intents preserve the exact channel without granting authority`() {
        val cold = channelNotificationRoute(CHANNEL_CLICK_ACTION, null, "channel_task_review", "class-B")
        val warm = channelNotificationRoute(null, "channel", "channel_task_submission", "class-B")
        assertEquals(ChannelNotificationRoute("class-B", openTasks = true), cold)
        assertEquals(cold, warm)
        assertEquals(
            ChannelNotificationRoute("class-A", openTasks = false),
            channelNotificationRoute(CHANNEL_CLICK_ACTION, null, "channel", "class-A"),
        )
    }

    @Test
    fun `task intents without a bounded channel never fall back to another classroom`() {
        for (channelId in listOf(null, "", " class-B", "class-B\n", "x".repeat(129))) {
            assertNull(channelNotificationRoute(CHANNEL_CLICK_ACTION, "channel", "channel_task", channelId))
        }
        assertNull(channelNotificationRoute(null, "attendance", "channel_task", "class-B"))
        assertEquals(
            ChannelNotificationRoute(null, openTasks = false),
            channelNotificationRoute(CHANNEL_CLICK_ACTION, null, "channel_notice", null),
        )
    }

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
