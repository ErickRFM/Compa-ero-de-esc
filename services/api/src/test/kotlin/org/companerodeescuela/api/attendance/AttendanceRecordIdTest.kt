package org.companerodeescuela.api.attendance

import kotlin.test.Test
import kotlin.test.assertNotEquals

class AttendanceRecordIdTest {
    @Test
    fun `record ids distinguish colon-delimited identity pairs`() {
        assertNotEquals(
            AttendanceRecordIds.forStudentSession("session:student", "entry"),
            AttendanceRecordIds.forStudentSession("session", "student:entry"),
        )
    }
}
