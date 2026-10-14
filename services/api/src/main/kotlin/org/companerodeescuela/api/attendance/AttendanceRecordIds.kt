package org.companerodeescuela.api.attendance

import java.security.MessageDigest

object AttendanceRecordIds {
    fun forStudentSession(
        sessionId: String,
        studentId: String,
    ): String {
        val canonical = "${sessionId.length}:$sessionId${studentId.length}:$studentId"
        val hasher = MessageDigest.getInstance("SHA-256")
        val digestBytes = hasher.digest(canonical.toByteArray(Charsets.UTF_8))
        val digest = digestBytes.joinToString(separator = "") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
        return "attendance:$digest"
    }
}
