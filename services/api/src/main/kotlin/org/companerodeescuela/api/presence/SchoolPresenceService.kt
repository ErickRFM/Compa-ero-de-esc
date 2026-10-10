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
    val institutionId:String?=null,
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
    private val accounts:org.companerodeescuela.api.auth.PlatformAccountRepository?=null,
) {
    private suspend fun nativeInstitution(studentId:String,actor:org.companerodeescuela.shared.contracts.UserSummary?):String? {
        if(actor==null) return null
        if(actor.id!=studentId) throw ApiException.Forbidden("School day belongs to another student")
        val current=org.companerodeescuela.api.admin.InstitutionScopePolicy(accounts ?: throw ApiException.DependencyUnavailable("Native identity is unavailable"),clock).live(actor)
        if(org.companerodeescuela.shared.contracts.UserRole.STUDENT !in current.roles || current.institutionId.isNullOrBlank()) throw ApiException.Forbidden("Verified student institution is required")
        return current.institutionId
    }
    suspend fun start(
        studentId: String,
        request: StartSchoolPresenceRequest,
        nativeActor:org.companerodeescuela.shared.contracts.UserSummary?=null,
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

        val institution=nativeInstitution(studentId,nativeActor)
        qrVerifier.verify(request.qrToken,institution,policy.institutionId)
        val method = networkVerifier.verify(request.network)
        val now = clock.instant()

        repository.findActiveForStudent(studentId, now.epochSecond)?.let {
            if(institution==null || it.institutionId==institution) return it.copy(networkVerified=false,serverTimeEpochSeconds=now.epochSecond)
            // Retain the unscoped legacy evidence, but end its active grant before creating a scoped school day.
            repository.replace(it.copy(status=SchoolPresenceStatus.CLOSED,closedAtEpochSeconds=now.epochSecond))
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
                institutionId=institution,
            ),
        ).copy(serverTimeEpochSeconds = now.epochSecond)
    }

    suspend fun activeFor(studentId: String,nativeActor:org.companerodeescuela.shared.contracts.UserSummary?=null): SchoolPresenceResponse? {
        val now = clock.instant().epochSecond
        val institution=nativeInstitution(studentId,nativeActor)
        return repository.findActiveForStudent(studentId, now)?.takeIf { institution==null || it.institutionId==institution }?.copy(networkVerified=false,serverTimeEpochSeconds=now)
    }

    suspend fun requireActive(studentId: String): SchoolPresenceResponse =
        activeFor(studentId)
            ?: throw ApiException.Forbidden(
                "Start your school day by scanning the institutional QR while connected to the school network",
            )

    suspend fun close(studentId: String,nativeActor:org.companerodeescuela.shared.contracts.UserSummary?=null): SchoolPresenceResponse {
        val now = clock.instant().epochSecond
        val institution=nativeInstitution(studentId,nativeActor)
        // Write from raw storage; read projections must not overwrite evidence.
        val current = repository.findActiveForStudent(studentId, now)?.takeIf { institution==null || it.institutionId==institution }
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
