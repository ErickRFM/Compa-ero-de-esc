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
    private val accounts:org.companerodeescuela.api.auth.PlatformAccountRepository?=null,
    private val mfa:org.companerodeescuela.api.security.MfaService?=null,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val tokenGenerator: () -> String = {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    },
    private val institutions:org.companerodeescuela.api.institutions.InstitutionRepository?=null,
) {
    private fun scopes()=org.companerodeescuela.api.admin.InstitutionScopePolicy(accounts ?: throw ApiException.DependencyUnavailable("Native accounts are unavailable"),clock)
    private fun identifier(value:String):String {
        if(value.isBlank() || value!=value.trim() || value.length>128 || value.any(Char::isISOControl)) throw ApiException.Validation("Bounded identifier is required")
        return value
    }
    private suspend fun requireProof(actor:org.companerodeescuela.api.auth.PlatformAccount,proof:String?,resource:String,operation:String,digest:String) {
        (mfa ?: throw ApiException.DependencyUnavailable("MFA is unavailable")).requireStepUp(actor,proof ?: throw ApiException.Forbidden("Scoped MFA proof is required"),org.companerodeescuela.shared.contracts.StepUpAction.MANAGE_ACADEMICS,resource,operation,digest)
    }
    suspend fun createScoped(actor:org.companerodeescuela.shared.contracts.UserSummary,request:CreateSchoolEntryQrRequest):SchoolEntryQrResponse {
        val institution=identifier(request.institutionId ?: throw ApiException.Forbidden("Explicit institution is required"))
        val current=scopes().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,institution)
        val operation=identifier(request.operationId ?: throw ApiException.Forbidden("Scoped operation is required"))
        val digest=org.companerodeescuela.shared.contracts.PrivilegedCommandDigests.schoolQrCreation(institution,request)
        requireProof(current,request.proof,institution,operation,digest)
        validate(request)
        if((institutions ?: throw ApiException.DependencyUnavailable("Institution directory is unavailable")).findEnabled(institution)==null) throw ApiException.Validation("Enabled institution is required")
        return insertScoped(current.id,operation,digest,institution,request)
    }
    private suspend fun insertScoped(actorId:String,operation:String,digest:String,institution:String,request:CreateSchoolEntryQrRequest):SchoolEntryQrResponse {
        val id="SQR-"+UUID.nameUUIDFromBytes((actorId+"\u0000"+operation).toByteArray(Charsets.UTF_8))
        val existing=repository.findById(id)
        if(existing!=null) {
            if(existing.response.institutionId!=institution || existing.response.operationId!=operation || existing.response.commandDigest!=digest || existing.response.updatedBy!=actorId) throw ApiException.Conflict("QR operation has another command")
            return existing.response.copy(token=null)
        }
        val token=tokenGenerator()
        val candidate=StoredSchoolEntryQr(SchoolEntryQrResponse(id,request.name.trim(),request.location?.trim()?.takeIf(String::isNotBlank),null,
            SchoolEntryQrStatus.ACTIVE,request.validFromEpochSeconds,request.expiresAtEpochSeconds,actorId,clock.instant().epochSecond,
            institutionId=institution,operationId=operation,commandDigest=digest,updatedBy=actorId,
            audit=listOf(org.companerodeescuela.shared.contracts.SchoolEntryQrAuditEntry(actorId,operation,digest,0,SchoolEntryQrStatus.ACTIVE,clock.instant().epochSecond))),sha256(token))
        val stored=repository.insertScoped(candidate) ?: throw ApiException.Conflict("QR was changed concurrently")
        return stored.response.copy(token=token.takeIf { stored.tokenHash==candidate.tokenHash })
    }
    suspend fun listScoped(actor:org.companerodeescuela.shared.contracts.UserSummary):List<SchoolEntryQrResponse> {
        val current=scopes().live(actor)
        if(org.companerodeescuela.shared.contracts.UserRole.SUPER_ADMIN !in current.roles) scopes().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,current.institutionId ?: "")
        val visible=mutableListOf<SchoolEntryQrResponse>()
        for(stored in repository.list()) {
            val item=stored.response
            val institution=item.institutionId
            if(institution.isNullOrBlank() || item.revision<0) continue
            try { scopes().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,institution) }
            catch (_:ApiException.Forbidden) { continue }
            visible+=item.copy(token=null,status=if(item.status==SchoolEntryQrStatus.ACTIVE && item.expiresAtEpochSeconds<=clock.instant().epochSecond) SchoolEntryQrStatus.EXPIRED else item.status)
        }
        return visible.sortedByDescending { it.createdAtEpochSeconds }
    }
    suspend fun revokeScoped(actor:org.companerodeescuela.shared.contracts.UserSummary,id:String,request:org.companerodeescuela.shared.contracts.AcademicOperationRequest):SchoolEntryQrResponse =
        revokeCommand(actor,id,request,org.companerodeescuela.shared.contracts.PrivilegedCommandDigests.schoolQrRevocation(id,request))
    private suspend fun revokeCommand(actor:org.companerodeescuela.shared.contracts.UserSummary,id:String,request:org.companerodeescuela.shared.contracts.AcademicOperationRequest,digest:String):SchoolEntryQrResponse {
        val stored=repository.findById(identifier(id)) ?: throw ApiException.NotFound("School entry QR was not found")
        val institution=stored.response.institutionId ?: throw ApiException.Forbidden("Unscoped QR requires explicit migration")
        val current=scopes().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,institution)
        identifier(request.operationId)
        requireProof(current,request.proof,id,request.operationId,digest)
        if(request.expectedRevision<0 || request.expectedRevision==Long.MAX_VALUE) throw ApiException.Validation("Bounded revision is required")
        if(stored.response.revision==request.expectedRevision+1 && stored.response.operationId==request.operationId && stored.response.commandDigest==digest && stored.response.updatedBy==current.id && stored.response.status==SchoolEntryQrStatus.REVOKED) return stored.response.copy(token=null)
        if(stored.response.revision!=request.expectedRevision) throw ApiException.Conflict("QR revision changed")
        if(stored.response.audit.size>=1000) throw ApiException.Conflict("QR audit capacity requires operator archival")
        val candidate=stored.copy(response=stored.response.copy(token=null,status=SchoolEntryQrStatus.REVOKED,revokedAtEpochSeconds=clock.instant().epochSecond,
            revision=request.expectedRevision+1,operationId=request.operationId,commandDigest=digest,updatedBy=current.id,
            audit=stored.response.audit+org.companerodeescuela.shared.contracts.SchoolEntryQrAuditEntry(current.id,request.operationId,digest,request.expectedRevision,SchoolEntryQrStatus.REVOKED,clock.instant().epochSecond)))
        return repository.replaceScoped(candidate,request.expectedRevision)?.response ?: throw ApiException.Conflict("QR was changed concurrently")
    }
    suspend fun regenerateScoped(actor:org.companerodeescuela.shared.contracts.UserSummary,id:String,request:org.companerodeescuela.shared.contracts.AcademicOperationRequest):RegenerateSchoolEntryQrResponse {
        val digest=org.companerodeescuela.shared.contracts.PrivilegedCommandDigests.schoolQrRegeneration(id,request)
        val revoked=revokeCommand(actor,id,request,digest)
        val operation="replacement-"+UUID.nameUUIDFromBytes((id+"\u0000"+request.operationId).toByteArray(Charsets.UTF_8))
        val replacement=insertScoped(actor.id,operation,digest,revoked.institutionId!!,CreateSchoolEntryQrRequest(revoked.name,revoked.location,
            clock.instant().epochSecond,maxOf(revoked.expiresAtEpochSeconds,clock.instant().epochSecond+3600)))
        return RegenerateSchoolEntryQrResponse(revoked,replacement)
    }
    private fun validate(request:CreateSchoolEntryQrRequest) {
        if(request.name.trim().length !in 1..120 || (request.location?.length ?: 0)>200 || request.name.any(Char::isISOControl)) throw ApiException.Validation("Bounded QR name and location are required")
        val now=clock.instant().epochSecond
        if(request.validFromEpochSeconds<0 || request.expiresAtEpochSeconds<=request.validFromEpochSeconds || request.expiresAtEpochSeconds<=now ||
            request.expiresAtEpochSeconds>now+366L*86400) throw ApiException.Validation("QR needs a future expiration within one year")
    }
    internal suspend fun create(
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

    internal suspend fun list(): List<SchoolEntryQrResponse> {
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

    internal suspend fun revoke(id: String): SchoolEntryQrResponse {
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

    internal suspend fun regenerate(
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

    internal suspend fun recognizes(rawToken:String):Boolean = repository.findByTokenHash(sha256(rawToken.trim()))!=null
    suspend fun verifyAndRecordUse(rawToken: String,institutionId:String?=null): SchoolEntryQrResponse? {
        val token = rawToken.trim()
        if (token.isBlank()) return null
        val stored = repository.findByTokenHash(sha256(token)) ?: return null
        val now = clock.instant().epochSecond
        val response = stored.response
        val eligible =
            response.status == SchoolEntryQrStatus.ACTIVE &&
                now >= response.validFromEpochSeconds &&
                now < response.expiresAtEpochSeconds
        if (!eligible || (institutionId!=null && (institutionId!=response.institutionId || response.revision<0))) return null

        val used = response.copy(
            token = null,
            lastUsedAtEpochSeconds = now,
            usageCount = response.usageCount + 1,
        )
        if(response.institutionId!=null) {
            if(response.revision<0 || response.revision==Long.MAX_VALUE) return null
            return repository.replaceScoped(stored.copy(response=used.copy(revision=response.revision+1)),response.revision)?.response
        }
        if(institutionId!=null) return null
        repository.replace(stored.copy(response = used))
        return used
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
