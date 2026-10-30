package org.companerodeescuela.notifications

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelNotificationIntentTest {
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
