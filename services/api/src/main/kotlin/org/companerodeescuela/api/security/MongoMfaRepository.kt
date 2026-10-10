package org.companerodeescuela.api.security

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Updates
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.shared.contracts.StepUpAction

/** Every use is a Mongo compare-and-set, including across distinct API processes. */
class MongoMfaRepository(database: MongoDatabase): MfaRepository {
    private val factors=database.getCollection<Document>("platform_mfa_factors")
    private val proofs=database.getCollection<Document>("platform_mfa_proofs")
    private val indexMutex=Mutex()
    @Volatile private var indexed=false
    override suspend fun findFactor(userId:String):MfaFactor? = factors.find(eq("_id",userId)).firstOrNull()?.factor()
    override suspend fun beginFactor(record:MfaFactor):Boolean {
        if(record.confirmedAt!=null || record.lastUsedStep != -1L || record.enrollmentRevision<0 || record.enrollmentExpiresAt<=record.createdAt) return false
        val previous=factors.find(eq("_id",record.userId)).firstOrNull()
        if(previous==null) return try { factors.insertOne(record.document());true } catch(error:MongoWriteException) {
            if(error.error.code==11000) false else throw error
        }
        val current=previous.factor() ?: return false
        if(current.confirmedAt!=null) return false
        return factors.replaceOne(factorSnapshot(current),record.document()).modifiedCount==1L
    }
    override suspend fun confirmFactor(userId:String,factorId:String,step:Long,now:Instant):Boolean {
        val current=findFactor(userId) ?: return false
        if(current.id!=factorId || current.confirmedAt!=null || current.createdAt>now || current.enrollmentExpiresAt<=now || step<0 || step<=current.lastUsedStep) return false
        return factors.updateOne(factorSnapshot(current),Updates.combine(Updates.set("confirmedAt",Date.from(now)),Updates.set("lastUsedStep",step))).modifiedCount==1L
    }
    override suspend fun consumeStep(userId:String,factorId:String,step:Long,now:Instant):Boolean {
        val current=findFactor(userId) ?: return false
        if(current.id!=factorId || current.confirmedAt==null || current.confirmedAt>now || step<0 || step<=current.lastUsedStep) return false
        return factors.updateOne(factorSnapshot(current),Updates.set("lastUsedStep",step)).modifiedCount==1L
    }
    override suspend fun putProof(record:MfaProofRecord):Boolean {
        if(record.binding.authRevision<0 || record.expiresAt<=record.createdAt || record.consumedAt!=null) return false
        ensureIndexes()
        return try { proofs.insertOne(record.document());true } catch(error:MongoWriteException) {
            if(error.error.code==11000) false else throw error
        }
    }
    override suspend fun consumeProof(hash:String,binding:StepUpBinding,now:Instant):Boolean {
        val current=proofs.find(eq("_id",hash)).firstOrNull()?.proof() ?: return false
        if(current.binding!=binding || current.createdAt>now || current.expiresAt<=now || current.consumedAt?.isAfter(now)==true) return false
        if(current.consumedAt!=null) return true
        val changed=proofs.updateOne(and(eq("_id",hash),eq("actorId",binding.actorId),eq("authRevision",binding.authRevision),eq("action",binding.action.name),eq("resourceId",binding.resourceId),eq("operationId",binding.operationId),eq("payloadDigest",binding.payloadDigest),eq("createdAt",Date.from(current.createdAt)),eq("expiresAt",Date.from(current.expiresAt)),eq("consumedAt",null)),Updates.set("consumedAt",Date.from(now)))
        if(changed.modifiedCount==1L) return true
        // Only an exact already-consumed command is retryable; its service must be idempotent too.
        val restored=proofs.find(eq("_id",hash)).firstOrNull()?.proof() ?: return false
        return restored.binding==binding && restored.createdAt<=now && restored.expiresAt>now && restored.consumedAt?.let { it<=now }==true
    }
    private suspend fun ensureIndexes() {
        if(indexed) return
        indexMutex.withLock {
            if(!indexed) { proofs.createIndex(Indexes.ascending("expiresAt"),IndexOptions().expireAfter(0L,TimeUnit.SECONDS).name("ttl_mfa_proof"));indexed=true }
        }
    }
    private fun factorSnapshot(record:MfaFactor)=and(eq("_id",record.userId),eq("factorId",record.id),eq("encryptedSecret",record.encryptedSecret),eq("enrollmentRevision",record.enrollmentRevision),eq("createdAt",Date.from(record.createdAt)),eq("enrollmentExpiresAt",Date.from(record.enrollmentExpiresAt)),eq("confirmedAt",record.confirmedAt?.let(Date::from)),eq("lastUsedStep",record.lastUsedStep))
    private fun MfaFactor.document()=Document("_id",userId).append("factorId",id).append("encryptedSecret",encryptedSecret).append("enrollmentRevision",enrollmentRevision).append("createdAt",Date.from(createdAt)).append("enrollmentExpiresAt",Date.from(enrollmentExpiresAt)).append("confirmedAt",confirmedAt?.let(Date::from)).append("lastUsedStep",lastUsedStep)
    private fun MfaProofRecord.document()=Document("_id",hash).append("actorId",binding.actorId).append("authRevision",binding.authRevision).append("action",binding.action.name).append("resourceId",binding.resourceId).append("operationId",binding.operationId).append("payloadDigest",binding.payloadDigest).append("createdAt",Date.from(createdAt)).append("expiresAt",Date.from(expiresAt)).append("consumedAt",consumedAt?.let(Date::from))
    private fun Document.factor():MfaFactor? = runCatching {
        val revision=integer("enrollmentRevision") ?: return null
        val step=integer("lastUsedStep") ?: return null
        if(revision<0 || step< -1) return null
        if(get("confirmedAt")!=null && get("confirmedAt") !is Date) return null
        val created=getDate("createdAt").toInstant();val expires=getDate("enrollmentExpiresAt").toInstant()
        val confirmed=getDate("confirmedAt")?.toInstant()
        if(expires<=created || (confirmed!=null && (confirmed<created || step<0))) return null
        MfaFactor(getString("factorId"),getString("_id"),getString("encryptedSecret"),revision,created,expires,confirmed,step)
    }.getOrNull()
    private fun Document.proof():MfaProofRecord? = runCatching {
        val revision=integer("authRevision") ?: return null
        if(revision<0 || (get("consumedAt")!=null && get("consumedAt") !is Date)) return null
        val created=getDate("createdAt").toInstant();val expires=getDate("expiresAt").toInstant();val consumed=getDate("consumedAt")?.toInstant()
        if(expires<=created || (consumed!=null && consumed<created)) return null
        MfaProofRecord(getString("_id"),StepUpBinding(getString("actorId"),revision,StepUpAction.valueOf(getString("action")),getString("resourceId"),getString("operationId"),getString("payloadDigest")),created,expires,consumed)
    }.getOrNull()
    private fun Document.integer(name:String):Long? = when(val value=get(name)) { is Long->value;is Int->value.toLong();else->null }
}
