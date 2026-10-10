package org.companerodeescuela.api.security

import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.StepUpAction

data class MfaFactor(val id: String,val userId: String,val encryptedSecret: String,val enrollmentRevision: Long,
    val createdAt: Instant,val enrollmentExpiresAt: Instant,val confirmedAt: Instant? = null,val lastUsedStep: Long = -1) {
    override fun toString()="MfaFactor(id=$id, confirmed=${confirmedAt!=null}, material=[redacted])"
}
data class StepUpBinding(val actorId: String,val authRevision: Long,val action: StepUpAction,
    val resourceId: String,val operationId: String,val payloadDigest: String)
data class MfaProofRecord(val hash: String,val binding: StepUpBinding,val createdAt: Instant,val expiresAt: Instant,val consumedAt: Instant? = null) {
    override fun toString()="MfaProofRecord(action=${binding.action}, proof=[redacted])"
}
interface MfaRepository {
    suspend fun findFactor(userId: String): MfaFactor?
    suspend fun beginFactor(record: MfaFactor): Boolean
    suspend fun confirmFactor(userId: String,factorId: String,step: Long,now: Instant): Boolean
    suspend fun consumeStep(userId: String,factorId: String,step: Long,now: Instant): Boolean
    suspend fun putProof(record: MfaProofRecord): Boolean
    suspend fun consumeProof(hash: String,binding: StepUpBinding,now: Instant): Boolean
}
class InMemoryMfaRepository : MfaRepository {
    private val mutex=Mutex()
    private val factors=mutableMapOf<String,MfaFactor>()
    private val proofs=mutableMapOf<String,MfaProofRecord>()
    override suspend fun findFactor(userId: String): MfaFactor? = mutex.withLock { factors[userId] }
    override suspend fun beginFactor(record: MfaFactor): Boolean = mutex.withLock {
        val previous=factors[record.userId]
        if(previous?.confirmedAt!=null || record.confirmedAt!=null || record.lastUsedStep != -1L ||
            record.enrollmentExpiresAt<=record.createdAt || record.enrollmentRevision<0) return@withLock false
        factors[record.userId]=record
        true
    }
    override suspend fun confirmFactor(userId: String,factorId: String,step: Long,now: Instant): Boolean = mutex.withLock {
        val factor=factors[userId] ?: return@withLock false
        if(factor.id!=factorId || factor.confirmedAt!=null || factor.createdAt>now || factor.enrollmentExpiresAt<=now || step<0 || step<=factor.lastUsedStep) return@withLock false
        factors[userId]=factor.copy(confirmedAt=now,lastUsedStep=step)
        true
    }
    override suspend fun consumeStep(userId: String,factorId: String,step: Long,now: Instant): Boolean = mutex.withLock {
        val factor=factors[userId] ?: return@withLock false
        if(factor.id!=factorId || factor.confirmedAt==null || factor.confirmedAt>now || step<0 || step<=factor.lastUsedStep) return@withLock false
        factors[userId]=factor.copy(lastUsedStep=step)
        true
    }
    override suspend fun putProof(record: MfaProofRecord): Boolean = mutex.withLock {
        proofs.entries.removeAll { it.value.expiresAt<=record.createdAt }
        if(record.hash in proofs || record.binding.authRevision<0 || record.expiresAt<=record.createdAt || record.consumedAt!=null) return@withLock false
        proofs[record.hash]=record
        true
    }
    override suspend fun consumeProof(hash: String,binding: StepUpBinding,now: Instant): Boolean = mutex.withLock {
        val proof=proofs[hash] ?: return@withLock false
        if(proof.binding!=binding || proof.createdAt>now || proof.expiresAt<=now || proof.consumedAt?.isAfter(now)==true) return@withLock false
        if(proof.consumedAt==null) proofs[hash]=proof.copy(consumedAt=now)
        true // Same command retry; changed action/resource/operation/payload cannot reuse it.
    }
}
