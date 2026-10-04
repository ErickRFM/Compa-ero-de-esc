package org.companerodeescuela.feature.attendance

import java.nio.charset.StandardCharsets
import java.util.Base64

object AttendanceQrTokenParser {
    fun sessionId(token: String): String? {
        val parts = token.trim().split('.')
        if (parts.size != 6 || parts.firstOrNull() != "v1") return null
        return runCatching {
            String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8,
            ).takeIf(String::isNotBlank)
        }.getOrNull()
    }
}
