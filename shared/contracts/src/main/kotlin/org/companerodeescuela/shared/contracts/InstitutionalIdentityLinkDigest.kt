package org.companerodeescuela.shared.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

/** Canonical MFA binding for a single institution identity mapping. */
object InstitutionalIdentityLinkDigest {
    private val json = Json { encodeDefaults = true }

    fun command(request: InstitutionalIdentityLinkRequest): String {
        val envelope =
            buildJsonObject {
                put("kind", "institutional-identity-link-v1")
                put("targetId", request.institutionId)
                put("command", json.encodeToJsonElement(request.copy(proof = "")))
            }
        val digest = MessageDigest.getInstance("SHA-256").digest(json.encodeToString(envelope).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
