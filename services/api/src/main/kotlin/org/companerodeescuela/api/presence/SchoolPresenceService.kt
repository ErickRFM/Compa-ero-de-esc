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

        activeRecord(studentId,institution,now.epochSecond)?.let { return it.copy(networkVerified=false,serverTimeEpochSeconds=now.epochSecond) }
        val candidate=SchoolPresenceResponse(
                id = if(institution==null) newId() else "DAY-"+UUID.nameUUIDFromBytes((studentId+"\u0000"+institution+"\u0000"+operationId).toByteArray(Charsets.UTF_8)),
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
                identitySource=if(institution==null) null else org.companerodeescuela.shared.contracts.SchoolPresenceIdentitySource.NATIVE,
                operationId=operationId.takeIf { institution!=null },
            )
        val stored=if(institution==null) repository.save(candidate) else repository.createNative(candidate,now.epochSecond)
            ?: throw ApiException.Conflict("School day operation has ended or changed")
        return stored.copy(serverTimeEpochSeconds=now.epochSecond)
    }

    private suspend fun activeRecord(studentId:String,institution:String?,now:Long)=
        if(institution==null) repository.findActiveForStudent(studentId,now) else repository.findActiveForNativeStudent(studentId,institution,now)
    suspend fun activeFor(studentId: String,nativeActor:org.companerodeescuela.shared.contracts.UserSummary?=null): SchoolPresenceResponse? {
        val now = clock.instant().epochSecond
        val institution=nativeInstitution(studentId,nativeActor)
        return activeRecord(studentId,institution,now)?.copy(networkVerified=false,serverTimeEpochSeconds=now)
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
        val current = activeRecord(studentId,institution,now)
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
