package org.companerodeescuela.api.presence

import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.NetworkVerificationMethod
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
        get() = entryQrSha256.isNotBlank() && (allowedSsids.isNotEmpty() || allowedBssids.isNotEmpty())
}

class SchoolPresenceService(
    private val repository: SchoolPresenceRepository,
    private val policy: SchoolPresencePolicy,
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

        verifyQr(request.qrToken)
        val method = verifyNetwork(request.network)
        val now = clock.instant()

        repository.findActiveForStudent(studentId, now.epochSecond)?.let { return it }

        return repository.save(
            SchoolPresenceResponse(
                id = newId(),
                studentId = studentId,
                startedAtEpochSeconds = now.epochSecond,
                expiresAtEpochSeconds = now.plus(Duration.ofHours(policy.sessionHours)).epochSecond,
                status = SchoolPresenceStatus.ACTIVE,
                qrVerified = true,
                networkVerified = true,
                networkVerificationMethod = method,
            ),
        )
    }

    suspend fun activeFor(studentId: String): SchoolPresenceResponse? {
        val now = clock.instant().epochSecond
        return repository.findActiveForStudent(studentId, now)
    }

    suspend fun requireActive(studentId: String): SchoolPresenceResponse =
        activeFor(studentId)
            ?: throw ApiException.Forbidden(
                "Start your school day by scanning the institutional QR while connected to the school network",
            )

    suspend fun close(studentId: String): SchoolPresenceResponse {
        val current = requireActive(studentId)
        val closed = current.copy(
            status = SchoolPresenceStatus.CLOSED,
            closedAtEpochSeconds = clock.instant().epochSecond,
        )
        repository.replace(closed)
        return closed
    }

    fun verifyNetworkForAttendance(network: SchoolNetworkEvidence): NetworkVerificationMethod =
        verifyNetwork(network)

    private fun verifyQr(rawToken: String) {
        val token = rawToken.trim()
        if (token.isBlank() || token.length > 2_048) {
            throw ApiException.Validation("Institutional QR is invalid")
        }

        val actual = sha256(token)
        if (!MessageDigest.isEqual(actual.toByteArray(), policy.entryQrSha256.lowercase().toByteArray())) {
            throw ApiException.Forbidden("Institutional QR could not be verified")
        }
    }

    private fun verifyNetwork(network: SchoolNetworkEvidence): NetworkVerificationMethod {
        val ssid = normalizeSsid(network.ssid)
        val bssid = normalizeBssid(network.bssid)
        val ssidOk = ssid != null && policy.allowedSsids.any { normalizeSsid(it) == ssid }
        val bssidOk = bssid != null && policy.allowedBssids.any { normalizeBssid(it) == bssid }

        if (policy.allowedSsids.isNotEmpty() && policy.allowedBssids.isNotEmpty()) {
            if (!ssidOk || !bssidOk) {
                throw ApiException.Forbidden("Connect to an authorized school Wi-Fi access point")
            }
            return NetworkVerificationMethod.SSID_BSSID
        }
        if (policy.allowedBssids.isNotEmpty()) {
            if (!bssidOk) throw ApiException.Forbidden("Connect to an authorized school Wi-Fi access point")
            return NetworkVerificationMethod.BSSID
        }
        if (!ssidOk) throw ApiException.Forbidden("Connect to the authorized school Wi-Fi network")
        return NetworkVerificationMethod.SSID
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun normalizeSsid(value: String?): String? =
        value?.trim()?.removePrefix(""")?.removeSuffix(""")?.takeIf { it.isNotBlank() }

    private fun normalizeBssid(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
}
