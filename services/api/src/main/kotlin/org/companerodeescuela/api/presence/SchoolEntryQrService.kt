package org.companerodeescuela.api.presence

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.CreateSchoolEntryQrRequest
import org.companerodeescuela.shared.contracts.RegenerateSchoolEntryQrResponse
import org.companerodeescuela.shared.contracts.SchoolEntryQrResponse
import org.companerodeescuela.shared.contracts.SchoolEntryQrStatus

class SchoolEntryQrService(
    private val repository: SchoolEntryQrRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val tokenGenerator: () -> String = {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    },
) {
    suspend fun create(
        actorId: String,
        request: CreateSchoolEntryQrRequest,
    ): SchoolEntryQrResponse {
        val name = request.name.trim()
        if (name.isBlank()) throw ApiException.Validation("QR name is required")
        if (request.expiresAtEpochSeconds <= request.validFromEpochSeconds) {
            throw ApiException.Validation("QR expiration must be after its start time")
        }

        val now = clock.instant().epochSecond
        val token = tokenGenerator()
        val response = SchoolEntryQrResponse(
            id = newId(),
            name = name,
            location = request.location?.trim()?.takeIf(String::isNotBlank),
            token = token,
            status = SchoolEntryQrStatus.ACTIVE,
            validFromEpochSeconds = request.validFromEpochSeconds,
            expiresAtEpochSeconds = request.expiresAtEpochSeconds,
            createdBy = actorId,
            createdAtEpochSeconds = now,
        )
        repository.save(StoredSchoolEntryQr(response.copy(token = null), sha256(token)))
        return response
    }

    suspend fun list(): List<SchoolEntryQrResponse> {
        val now = clock.instant().epochSecond
        return repository.list()
            .map { stored ->
                val response = stored.response
                if (
                    response.status == SchoolEntryQrStatus.ACTIVE &&
                    response.expiresAtEpochSeconds <= now
                ) {
                    val expired = response.copy(status = SchoolEntryQrStatus.EXPIRED, token = null)
                    repository.replace(stored.copy(response = expired))
                    expired
                } else {
                    response.copy(token = null)
                }
            }
            .sortedByDescending { it.createdAtEpochSeconds }
    }

    suspend fun revoke(id: String): SchoolEntryQrResponse {
        val stored = repository.findById(id)
            ?: throw ApiException.NotFound("School entry QR was not found")
        if (stored.response.status == SchoolEntryQrStatus.REVOKED) {
            return stored.response.copy(token = null)
        }
        val revoked = stored.response.copy(
            token = null,
            status = SchoolEntryQrStatus.REVOKED,
            revokedAtEpochSeconds = clock.instant().epochSecond,
        )
        repository.replace(stored.copy(response = revoked))
        return revoked
    }

    suspend fun regenerate(
        actorId: String,
        id: String,
    ): RegenerateSchoolEntryQrResponse {
        val current = repository.findById(id)
            ?: throw ApiException.NotFound("School entry QR was not found")
        val revoked = revoke(id)
        val replacement = create(
            actorId = actorId,
            request = CreateSchoolEntryQrRequest(
                name = current.response.name,
                location = current.response.location,
                validFromEpochSeconds = clock.instant().epochSecond,
                expiresAtEpochSeconds = current.response.expiresAtEpochSeconds
                    .coerceAtLeast(clock.instant().epochSecond + 3600),
            ),
        )
        return RegenerateSchoolEntryQrResponse(revoked, replacement)
    }

    suspend fun verifyAndRecordUse(rawToken: String): SchoolEntryQrResponse? {
        val token = rawToken.trim()
        if (token.isBlank()) return null
        val stored = repository.findByTokenHash(sha256(token)) ?: return null
        val now = clock.instant().epochSecond
        val response = stored.response
        val eligible =
            response.status == SchoolEntryQrStatus.ACTIVE &&
                now >= response.validFromEpochSeconds &&
                now < response.expiresAtEpochSeconds
        if (!eligible) return null

        val used = response.copy(
            token = null,
            lastUsedAtEpochSeconds = now,
            usageCount = response.usageCount + 1,
        )
        repository.replace(stored.copy(response = used))
        return used
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
