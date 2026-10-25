package org.companerodeescuela.notifications

import org.companerodeescuela.shared.contracts.AttendancePushContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttendanceNotificationIntentTest {
    @Test fun `only a canonical attendance action can select a session`() {
        val valid = "11111111-1111-4111-8111-111111111111"
        assertEquals(valid, parseAttendanceSessionId(AttendancePushContract.CLICK_ACTION, valid))
        assertNull(parseAttendanceSessionId("android.intent.action.MAIN", valid))
        assertNull(parseAttendanceSessionId(AttendancePushContract.CLICK_ACTION, "other-session"))
        assertNull(parseAttendanceSessionId(AttendancePushContract.CLICK_ACTION, "11111111111141118111111111111111"))
        assertNull(parseAttendanceSessionId(AttendancePushContract.CLICK_ACTION, null))
    }
}
