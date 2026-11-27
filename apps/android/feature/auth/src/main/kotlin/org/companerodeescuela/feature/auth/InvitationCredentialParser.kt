package org.companerodeescuela.feature.auth

internal data class ParsedInvitationCredentials(
    val invitationId: String,
    val code: String,
)

/**
 * Extracts a 43-char base64url invitation ID and 43-char code from:
 * 1) Unified activation key `<invitationId>.<code>` (or `<invitationId>:<code>`)
 * 2) Copied email body containing `Código...` and `ID...`
 */
internal object InvitationCredentialParser {
    private val TOKEN_REGEX = Regex("[A-Za-z0-9_-]{43}")
    private val COMBINED_REGEX = Regex("([A-Za-z0-9_-]{43})[.:]([A-Za-z0-9_-]{43})")
    private val LABELED_ID_REGEX =
        Regex(
            """(?i)\bID(?:\s+de\s+invitaci[oó]n)?\s*:\s*([A-Za-z0-9_-]{43})\b""",
        )
    private val LABELED_CODE_REGEX =
        Regex(
            """(?i)c[oó]digo(?:\s+de\s+invitaci[oó]n|\s+recibido|\s+individual)?\s*:?\s*([A-Za-z0-9_-]{43})\b""",
        )

    fun isValidPart(value: String): Boolean = TOKEN_REGEX.matches(value.trim())

    fun parse(raw: String): ParsedInvitationCredentials? {
        val normalized = raw.trim()
        if (normalized.isEmpty()) return null

        COMBINED_REGEX.find(normalized)?.let { match ->
            return ParsedInvitationCredentials(
                invitationId = match.groupValues[1],
                code = match.groupValues[2],
            )
        }

        val labeledId = LABELED_ID_REGEX.find(normalized)?.groupValues?.get(1)
        val labeledCode = LABELED_CODE_REGEX.find(normalized)?.groupValues?.get(1)
        if (labeledId != null && labeledCode != null && labeledId != labeledCode) {
            return ParsedInvitationCredentials(invitationId = labeledId, code = labeledCode)
        }

        val tokens =
            TOKEN_REGEX
                .findAll(normalized)
                .map { it.value }
                .distinct()
                .toList()
        if (tokens.size == 2) {
            if (labeledId == tokens[0]) return ParsedInvitationCredentials(tokens[0], tokens[1])
            if (labeledId == tokens[1]) return ParsedInvitationCredentials(tokens[1], tokens[0])
            return ParsedInvitationCredentials(invitationId = tokens[0], code = tokens[1])
        }
        return null
    }
}
