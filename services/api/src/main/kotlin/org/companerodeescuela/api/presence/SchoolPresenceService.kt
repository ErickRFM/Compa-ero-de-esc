package org.companerodeescuela.api.presence

import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.SchoolNetworkEvidence
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.SchoolPresenceStatus
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest

data class SchoolPresencePolicy(
    val entryQrSha256: String,
    val allowedSsids: Set<String>,
    val allowedBssids: Set<String>,
    val sessionHours: Long = 14,
) {
    val enabled: Boolean
        get() = allowedSsids.isNotEmpty() || allowedBssids.isNotEmpty()
}

class SchoolPresenceService(
    private val repository: SchoolPresenceRepository,
    private val policy: SchoolPresencePolicy,
    private val qrVerifier: SchoolEntryQrVerifier = SchoolEntryQrVerifier(
        legacyQrSha256 = policy.entryQrSha256,
    ),
    private val networkVerifier: SchoolNetworkVerifier = SchoolNetworkVerifier(
        allowedSsids = policy.allowedSsids,
        allowedBssids = policy.allowedBssids,
    ),
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun start(
        studentId: String,
        request: StartSchoolPresenceRequest,
    ): SchoolPresenceResponse {
        if (!policy.enabled) {
            throw ApiException.DependencyUnavailable("School presence verification is not configured")
        }

        val operationId = request.operationId.trim()
        if (operationId.isBlank() || operationId.length > 128) {
            throw ApiException.Validation("operationId must be between 1 and 128 characters")
        }
        if (request.deviceTimestampEpochSeconds <= 0) {
            throw ApiException.Validation("device timestamp is invalid")
        }

        qrVerifier.verify(request.qrToken)
        val method = networkVerifier.verify(request.network)
        val now = clock.instant()

        repository.findActiveForStudent(studentId, now.epochSecond)?.let {
            return it.copy(networkVerified = false, serverTimeEpochSeconds = now.epochSecond)
        }

        return repository.save(
            SchoolPresenceResponse(
                id = newId(),
                studentId = studentId,
                startedAtEpochSeconds = now.epochSecond,
                expiresAtEpochSeconds = now.plus(Duration.ofHours(policy.sessionHours)).epochSecond,
                status = SchoolPresenceStatus.ACTIVE,
                qrVerified = true,
                // Matching client-declared SSID/BSSID is not an authenticated
                // network witness. No independent verifier is configured.
                networkVerified = false,
                networkVerificationMethod = method,
            ),
        ).copy(serverTimeEpochSeconds = now.epochSecond)
    }

    suspend fun activeFor(studentId: String): SchoolPresenceResponse? {
        val now = clock.instant().epochSecond
        return repository.findActiveForStudent(studentId, now)?.copy(networkVerified = false, serverTimeEpochSeconds = now)
    }

    suspend fun requireActive(studentId: String): SchoolPresenceResponse =
        activeFor(studentId)
            ?: throw ApiException.Forbidden(
                "Start your school day by scanning the institutional QR while connected to the school network",
            )

    suspend fun close(studentId: String): SchoolPresenceResponse {
        val now = clock.instant().epochSecond
        // Write from raw storage; read projections must not overwrite evidence.
        val current = repository.findActiveForStudent(studentId, now)
            ?: throw ApiException.Forbidden(
                "Start your school day by scanning the institutional QR while connected to the school network",
            )
        val closed = current.copy(
            status = SchoolPresenceStatus.CLOSED,
            closedAtEpochSeconds = now,
        )
        repository.replace(closed)
        return closed.copy(networkVerified = false, serverTimeEpochSeconds = now)
    }

    /** Declaration policy match only; never proof of physical presence. */
    fun verifyNetworkForAttendance(network: SchoolNetworkEvidence) =
        networkVerifier.verify(network)
}
