package org.companerodeescuela.api.attendance

import java.security.MessageDigest

object AttendanceRecordIds {
    fun forStudentSession(sessionId: String, studentId: String): String {
        val canonical = "${sessionId.length}:$sessionId${studentId.length}:$studentId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
        return "attendance:$digest"
    }
}
