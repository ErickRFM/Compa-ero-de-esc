package org.companerodeescuela.api.security

import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import java.util.UUID
import org.companerodeescuela.api.auth.PasswordHasher
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*

/** MFA evidence is bound to a specific command; the consuming service still checks live permission and scope. */
class MfaService(private val accounts: PlatformAccountRepository,private val repository: MfaRepository,
    private val cipher: MfaSecretCipher?,private val clock: Clock = Clock.systemUTC(),private val hasher: PasswordHasher = PasswordHasher()) {
    private val random=SecureRandom()
    suspend fun status(actor: PlatformAccount): MfaStatus {
        val current=liveActor(actor)
        return MfaStatus(repository.findFactor(current.id)?.confirmedAt?.let { it<=clock.instant() }==true,cipher!=null)
    }
    suspend fun beginEnrollment(actor: PlatformAccount,password: String): MfaEnrollment {
        val current=verifiedActor(actor);val encryption=configuredCipher()
        reauthenticate(current,password)
        val now=clock.instant();val id=UUID.randomUUID().toString()
        val secret=ByteArray(20).also(random::nextBytes)
        try {
            val encrypted=encryption.encrypt(current.id,id,secret)
            val factor=MfaFactor(id,current.id,encrypted,current.authRevision,now,now.plusSeconds(600))
            if(!repository.beginFactor(factor)) throw ApiException.Forbidden("An enrolled factor cannot be replaced by password alone")
            val encoded=Totp.encodeSecret(secret)
            val label=encode("Companero de Clase:"+current.email)
            val uri="otpauth://totp/$label?secret=$encoded&issuer="+encode("Companero de Clase")+"&algorithm=SHA1&digits=6&period=30"
            return MfaEnrollment(id,encoded,uri,factor.enrollmentExpiresAt.epochSecond)
        } finally { secret.fill(0) }
    }
    suspend fun confirmEnrollment(actor: PlatformAccount,enrollmentId: String,otp: String): MfaStatus {
        val current=verifiedActor(actor);val encryption=configuredCipher();val now=clock.instant()
        val factor=repository.findFactor(current.id) ?: throw ApiException.Forbidden("No pending factor is available")
        if(factor.id!=enrollmentId || factor.enrollmentRevision!=current.authRevision || factor.confirmedAt!=null || factor.createdAt>now || factor.enrollmentExpiresAt<=now) throw ApiException.Forbidden("MFA enrollment is not current")
        val secret=readSecret(encryption,factor)
        try {
            val step=Totp.matchingStep(secret,otp,now) ?: throw ApiException.Forbidden("MFA code is invalid")
            if(!repository.confirmFactor(current.id,factor.id,step,now)) throw ApiException.Forbidden("MFA enrollment was already used")
            return MfaStatus(true,true)
        } finally { secret.fill(0) }
    }
    suspend fun issueStepUp(actor: PlatformAccount,password: String,otp: String,action: StepUpAction,resourceId: String,operationId: String,payloadDigest: String): StepUpProof {
        val current=verifiedActor(actor);val encryption=configuredCipher()
        if(current.accountStatus!=AccountStatus.ACTIVE || !permitsIntent(current,action)) throw ApiException.Forbidden("This MFA purpose is not available to the account")
        validateIntent(resourceId,operationId,payloadDigest)
        reauthenticate(current,password)
        val now=clock.instant();val factor=repository.findFactor(current.id) ?: throw ApiException.Forbidden("An enrolled MFA factor is required")
        if(factor.confirmedAt==null || factor.confirmedAt>now) throw ApiException.Forbidden("An enrolled MFA factor is required")
        val secret=readSecret(encryption,factor)
        try {
            val step=Totp.matchingStep(secret,otp,now) ?: throw ApiException.Forbidden("MFA code is invalid")
            if(!repository.consumeStep(current.id,factor.id,step,now)) throw ApiException.Forbidden("MFA code was already used")
            val token=Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
            val binding=StepUpBinding(current.id,current.authRevision,action,resourceId,operationId,payloadDigest)
            val proof=MfaProofRecord(hash(token),binding,now,now.plusSeconds(300))
            if(!repository.putProof(proof)) throw ApiException.DependencyUnavailable("MFA proof could not be stored")
            return StepUpProof(token,proof.expiresAt.epochSecond)
        } finally { secret.fill(0) }
    }
    suspend fun requireStepUp(actor: PlatformAccount,proof: String,action: StepUpAction,resourceId: String,operationId: String,payloadDigest: String) {
        val current=verifiedActor(actor);configuredCipher()
        if(current.accountStatus!=AccountStatus.ACTIVE || !permitsIntent(current,action)) throw ApiException.Forbidden()
        validateIntent(resourceId,operationId,payloadDigest)
        if(!Regex("[A-Za-z0-9_-]{43}").matches(proof)) throw ApiException.Forbidden("MFA proof is invalid")
        val binding=StepUpBinding(current.id,current.authRevision,action,resourceId,operationId,payloadDigest)
        if(!repository.consumeProof(hash(proof),binding,clock.instant())) throw ApiException.Forbidden("Current scoped MFA proof is required")
    }
    private suspend fun liveActor(actor:PlatformAccount):PlatformAccount {
        val current=accounts.findById(actor.id) ?: throw ApiException.Unauthorized()
        if(!current.permitsSession || current.authRevision!=actor.authRevision || current.accountStatus!=actor.accountStatus ||
            current.roles!=actor.roles || current.institutionId!=actor.institutionId || (current.accountStatus==AccountStatus.ACTIVE && current.roles.isEmpty())) throw ApiException.Unauthorized()
        return current
    }
    private suspend fun verifiedActor(actor:PlatformAccount):PlatformAccount = liveActor(actor).also {
        if(it.emailVerifiedAt==null || it.emailVerifiedAt>clock.instant() || it.accountStatus !in setOf(AccountStatus.ACTIVE,AccountStatus.PENDING_APPROVAL)) throw ApiException.Forbidden("Verified email is required")
    }
    private fun configuredCipher():MfaSecretCipher=cipher ?: throw ApiException.DependencyUnavailable("MFA encryption is not configured")
    private fun reauthenticate(actor:PlatformAccount,password:String) {
        if(password.length !in 8..256 || password.any(Char::isISOControl) || !hasher.verify(password,actor.passwordHash)) throw ApiException.Forbidden("Reauthentication is required")
    }
    private fun permitsIntent(actor:PlatformAccount,action:StepUpAction):Boolean {
        if(action==StepUpAction.PRIVILEGED_INVITE_ACCEPT) return true
        if(UserRole.SUPER_ADMIN in actor.roles) return true
        if(UserRole.ADMIN !in actor.roles || actor.institutionId.isNullOrBlank()) return false
        return action !in setOf(StepUpAction.ADMIN_INVITE,StepUpAction.ADMIN_AUTHORITY,StepUpAction.INSTITUTION_MANAGEMENT)
    }
    private fun validateIntent(resource:String,operation:String,digest:String) {
        if(resource.isBlank() || resource!=resource.trim() || resource.length>128 || resource.any(Char::isISOControl) ||
            operation.isBlank() || operation!=operation.trim() || operation.length>128 || operation.any(Char::isISOControl) || !Regex("[a-f0-9]{64}").matches(digest)) throw ApiException.Validation("MFA command scope is invalid")
    }
    private fun readSecret(encryption:MfaSecretCipher,factor:MfaFactor):ByteArray = try {
        encryption.decrypt(factor.userId,factor.id,factor.encryptedSecret)
    } catch (_:Exception) { throw ApiException.DependencyUnavailable("MFA factor could not be read") }
    private fun hash(token:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII)))
    private fun encode(value:String)=URLEncoder.encode(value,Charsets.UTF_8).replace("+","%20")
}
