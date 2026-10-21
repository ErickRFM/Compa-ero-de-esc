package org.companerodeescuela.api.integrations.push

import java.security.MessageDigest
import java.time.Clock
import java.util.Base64

/**
 * Explicit, one-time, operator-approved bootstrap. Does not grant roles or create
 * institutions. Disabled unless the complete flag and credentials are supplied.
 *
 * After successful deployment, remove the bootstrap secrets from the environment;
 * the encrypted integration credential is persisted in MongoDB.
 */
object SiiiPilotCredentialBootstrap {
    fun parse(
        env: (String) -> String?,
        clock: Clock = Clock.systemUTC(),
    ): IntegrationCredential? {
        val enabled = env("SIII_INBOUND_BOOTSTRAP_ENABLED")?.trim() ?: "false"
        require(enabled in setOf("true", "false")) {
            "SIII_INBOUND_BOOTSTRAP_ENABLED must be true or false"
        }
        if (enabled == "false") return null

        fun required(name: String): String =
            env(name)?.takeIf { it.isNotBlank() && it == it.trim() }
                ?: throw IllegalArgumentException("$name is required for institutional bootstrap")

        val keyId = required("SIII_INBOUND_KEY_ID")
        val tenantId = required("SIII_INBOUND_TENANT_ID")
        require(keyId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "SIII_INBOUND_KEY_ID is invalid" }
        require(tenantId.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) {
            "SIII_INBOUND_TENANT_ID is invalid"
        }

        val encoded = required("SIII_INBOUND_SECRET_BASE64")
        val secret =
            try {
                Base64.getDecoder().decode(encoded)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("SIII_INBOUND_SECRET_BASE64 is invalid")
            }
        require(secret.size in 32..128 && Base64.getEncoder().encodeToString(secret) == encoded) {
            "SIII_INBOUND_SECRET_BASE64 must be canonical Base64 of 32 to 128 random bytes"
        }

        val expiry =
            required("SIII_INBOUND_EXPIRES_AT_EPOCH_SECONDS").toLongOrNull()
                ?: throw IllegalArgumentException("SIII_INBOUND_EXPIRES_AT_EPOCH_SECONDS is invalid")
        val now = clock.instant().epochSecond
        require(expiry > now + 3_600 && expiry <= now + 90L * 86_400) {
            "SIII_INBOUND_EXPIRES_AT_EPOCH_SECONDS must be between one hour and 90 days ahead"
        }
        return IntegrationCredential(
            keyId = keyId,
            tenantId = tenantId,
            secret = secret,
            scopes = setOf("group:write"),
            enabled = true,
            expiresAtEpochSeconds = expiry,
        )
    }

    suspend fun provision(
        repository: MongoInstitutionalPushRepository,
        desired: IntegrationCredential,
    ) {
        val existing = repository.findCredential(desired.keyId)
        if (existing == null) {
            repository.putCredential(desired)
            return
        }
        require(
            existing.enabled &&
                existing.tenantId == desired.tenantId &&
                existing.scopes == desired.scopes &&
                existing.expiresAtEpochSeconds == desired.expiresAtEpochSeconds &&
                MessageDigest.isEqual(existing.secret, desired.secret),
        ) {
            "Institutional key ID already exists with different or revoked credentials; aborting bootstrap"
        }
        // Idempotent: never overwrite or resurrect a revoked credential.
    }
}
