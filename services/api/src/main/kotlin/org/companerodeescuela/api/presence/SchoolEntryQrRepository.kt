package org.companerodeescuela.api.presence

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.MongoWriteException
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.companerodeescuela.shared.contracts.SchoolEntryQrAuditEntry
import org.companerodeescuela.shared.contracts.SchoolEntryQrResponse
import org.companerodeescuela.shared.contracts.SchoolEntryQrStatus

data class StoredSchoolEntryQr(
    val response: SchoolEntryQrResponse,
    val tokenHash: String,
)

interface SchoolEntryQrRepository {
    suspend fun save(value: StoredSchoolEntryQr): StoredSchoolEntryQr
    suspend fun findById(id: String): StoredSchoolEntryQr?
    suspend fun list(): List<StoredSchoolEntryQr>
    suspend fun findByTokenHash(tokenHash: String): StoredSchoolEntryQr?
    suspend fun insertScoped(value:StoredSchoolEntryQr):StoredSchoolEntryQr? = null
    suspend fun replaceScoped(value:StoredSchoolEntryQr,expectedRevision:Long):StoredSchoolEntryQr? = null
    suspend fun replace(value: StoredSchoolEntryQr)
}

class InMemorySchoolEntryQrRepository : SchoolEntryQrRepository {
    private val mutex = Mutex()
    private val values = linkedMapOf<String, StoredSchoolEntryQr>()

    override suspend fun save(value: StoredSchoolEntryQr): StoredSchoolEntryQr =
        mutex.withLock {
            values[value.response.id] = value
            value
        }

    override suspend fun findById(id: String): StoredSchoolEntryQr? =
        mutex.withLock { values[id] }

    override suspend fun list(): List<StoredSchoolEntryQr> =
        mutex.withLock { values.values.toList() }

    override suspend fun findByTokenHash(tokenHash: String): StoredSchoolEntryQr? =
        mutex.withLock { values.values.firstOrNull { it.tokenHash == tokenHash } }

    override suspend fun insertScoped(value:StoredSchoolEntryQr):StoredSchoolEntryQr? = mutex.withLock {
        if(!value.validScopedCreation()) return@withLock null
        val current=values[value.response.id]
        if(current!=null) return@withLock current.takeIf { it.exactCreation(value) }
        values[value.response.id]=value
        value
    }
    override suspend fun replaceScoped(value:StoredSchoolEntryQr,expectedRevision:Long):StoredSchoolEntryQr? = mutex.withLock {
        if(!value.validScopedMutation(expectedRevision)) return@withLock null
        val current=values[value.response.id] ?: return@withLock null
        if(current==value) return@withLock current
        if(current.response.revision!=expectedRevision || current.response.institutionId!=value.response.institutionId ||
            (current.response.status!=SchoolEntryQrStatus.ACTIVE && value.response.status==SchoolEntryQrStatus.ACTIVE)) return@withLock null
        values[value.response.id]=value
        value
    }
    override suspend fun replace(value: StoredSchoolEntryQr) {
        mutex.withLock { values[value.response.id] = value }
    }
}

class MongoSchoolEntryQrRepository(
    database: MongoDatabase,
) : SchoolEntryQrRepository {
    private val collection: MongoCollection<Document> =
        database.getCollection(COLLECTION)

    override suspend fun save(value: StoredSchoolEntryQr): StoredSchoolEntryQr {
        ensureIndexes()
        collection.insertOne(value.toDocument())
        return value
    }

    override suspend fun findById(id: String): StoredSchoolEntryQr? {
        ensureIndexes()
        return collection.find(eq("_id", id)).toList().firstOrNull()?.toStored()
    }

    override suspend fun list(): List<StoredSchoolEntryQr> {
        ensureIndexes()
        return collection.find().toList().map { it.toStored() }
    }

    override suspend fun findByTokenHash(tokenHash: String): StoredSchoolEntryQr? {
        ensureIndexes()
        return collection.find(eq("tokenHash", tokenHash)).toList().firstOrNull()?.toStored()
    }

    override suspend fun insertScoped(value:StoredSchoolEntryQr):StoredSchoolEntryQr? {
        if(!value.validScopedCreation()) return null
        ensureIndexes()
        return try { collection.insertOne(value.toDocument());value }
        catch(error:MongoWriteException) {
            if(error.error.code!=11000) throw error
            findById(value.response.id)?.takeIf { it.exactCreation(value) }
        }
    }
    override suspend fun replaceScoped(value:StoredSchoolEntryQr,expectedRevision:Long):StoredSchoolEntryQr? {
        if(!value.validScopedMutation(expectedRevision)) return null
        ensureIndexes()
        val current=findById(value.response.id) ?: return null
        if(current==value) return current
        if(current.response.revision!=expectedRevision || current.response.institutionId!=value.response.institutionId ||
            (current.response.status!=SchoolEntryQrStatus.ACTIVE && value.response.status==SchoolEntryQrStatus.ACTIVE)) return null
        val filter=and(eq("_id",value.response.id),eq("revision",expectedRevision),eq("institutionId",value.response.institutionId),eq("status",current.response.status.name))
        return collection.findOneAndReplace(filter,value.toDocument(),FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER))?.toStored()
            ?: findById(value.response.id)?.takeIf { it==value }
    }
    override suspend fun replace(value: StoredSchoolEntryQr) {
        ensureIndexes()
        collection.replaceOne(eq("_id", value.response.id), value.toDocument())
    }

    private var indexed = false
    private suspend fun ensureIndexes() {
        if (indexed) return
        collection.createIndex(
            Indexes.ascending("tokenHash"),
            IndexOptions().unique(true).name("ux_school_entry_qr_token_hash"),
        )
        collection.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("status"),
                Indexes.ascending("expiresAtEpochSeconds"),
            ),
            IndexOptions().name("ix_school_entry_qr_status_expiry"),
        )
        indexed = true
    }

    private fun StoredSchoolEntryQr.toDocument(): Document = Document()
        .append("_id", response.id)
        .append("name", response.name)
        .append("location", response.location)
        .append("tokenHash", tokenHash)
        .append("status", response.status.name)
        .append("validFromEpochSeconds", response.validFromEpochSeconds)
        .append("expiresAtEpochSeconds", response.expiresAtEpochSeconds)
        .append("createdBy", response.createdBy)
        .append("createdAtEpochSeconds", response.createdAtEpochSeconds)
        .append("revokedAtEpochSeconds", response.revokedAtEpochSeconds)
        .append("lastUsedAtEpochSeconds", response.lastUsedAtEpochSeconds)
        .append("usageCount", response.usageCount)
        .append("institutionId",response.institutionId)
        .append("revision",response.revision)
        .append("operationId",response.operationId)
        .append("commandDigest",response.commandDigest)
        .append("updatedBy",response.updatedBy)
        .append("audit",Json.encodeToString(response.audit))

    private fun Document.toStored(): StoredSchoolEntryQr {
        val audit=runCatching { getString("audit")?.let { Json.decodeFromString<List<SchoolEntryQrAuditEntry>>(it) } ?: emptyList() }.getOrNull()
        val validAudit=audit?.takeIf { it.size<=1000 && it.all { row -> row.actorId.isNotBlank() && row.operationId.isNotBlank() && row.commandDigest.matches(Regex("[0-9a-f]{64}")) && row.fromRevision>=0 } }
        val response = SchoolEntryQrResponse(
            id = getString("_id"),
            name = getString("name"),
            location = getString("location"),
            token = null,
            status = SchoolEntryQrStatus.valueOf(getString("status")),
            validFromEpochSeconds = number("validFromEpochSeconds"),
            expiresAtEpochSeconds = number("expiresAtEpochSeconds"),
            createdBy = getString("createdBy"),
            createdAtEpochSeconds = number("createdAtEpochSeconds"),
            revokedAtEpochSeconds = optionalNumber("revokedAtEpochSeconds"),
            lastUsedAtEpochSeconds = optionalNumber("lastUsedAtEpochSeconds"),
            usageCount = optionalNumber("usageCount") ?: 0,
            audit=validAudit ?: emptyList(),institutionId=getString("institutionId"),revision=if(validAudit==null) -1 else strictRevision(),operationId=getString("operationId"),commandDigest=getString("commandDigest"),updatedBy=getString("updatedBy"),
        )
        return StoredSchoolEntryQr(response, getString("tokenHash"))
    }

    private fun Document.strictRevision():Long = when(val value=get("revision")) { is Long -> value;is Int -> value.toLong();else -> -1 }
    private fun Document.number(name: String): Long =
        (get(name) as? Number)?.toLong() ?: error("Missing numeric field: $name")
    private fun Document.optionalNumber(name: String): Long? =
        (get(name) as? Number)?.toLong()

    private companion object {
        const val COLLECTION = "school_entry_qrs"
    }
}

private fun StoredSchoolEntryQr.validScopedCreation() = !response.institutionId.isNullOrBlank() && response.revision==0L &&
    !response.operationId.isNullOrBlank() && response.commandDigest?.matches(Regex("[0-9a-f]{64}"))==true && !response.updatedBy.isNullOrBlank() && response.token==null && response.audit.size<=1000
private fun StoredSchoolEntryQr.validScopedMutation(expected:Long) = expected>=0 && expected<Long.MAX_VALUE && response.revision==expected+1 &&
    !response.institutionId.isNullOrBlank() && !response.operationId.isNullOrBlank() && response.commandDigest?.matches(Regex("[0-9a-f]{64}"))==true && !response.updatedBy.isNullOrBlank() && response.token==null && response.audit.size<=1000
private fun StoredSchoolEntryQr.exactCreation(other:StoredSchoolEntryQr) = response.copy(createdAtEpochSeconds=0,lastUsedAtEpochSeconds=null,usageCount=0,revision=0,audit=emptyList())==
    other.response.copy(createdAtEpochSeconds=0,lastUsedAtEpochSeconds=null,usageCount=0,revision=0,audit=emptyList())
