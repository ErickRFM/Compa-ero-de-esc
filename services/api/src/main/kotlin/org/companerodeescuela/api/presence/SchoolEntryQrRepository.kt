package org.companerodeescuela.api.presence

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.shared.contracts.SchoolEntryQrResponse
import org.companerodeescuela.shared.contracts.SchoolEntryQrStatus

internal data class StoredSchoolEntryQr(
    val response: SchoolEntryQrResponse,
    val tokenHash: String,
)

interface SchoolEntryQrRepository {
    suspend fun save(value: StoredSchoolEntryQr): StoredSchoolEntryQr
    suspend fun findById(id: String): StoredSchoolEntryQr?
    suspend fun list(): List<StoredSchoolEntryQr>
    suspend fun findByTokenHash(tokenHash: String): StoredSchoolEntryQr?
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

    private fun Document.toStored(): StoredSchoolEntryQr {
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
        )
        return StoredSchoolEntryQr(response, getString("tokenHash"))
    }

    private fun Document.number(name: String): Long =
        (get(name) as? Number)?.toLong() ?: error("Missing numeric field: $name")
    private fun Document.optionalNumber(name: String): Long? =
        (get(name) as? Number)?.toLong()

    private companion object {
        const val COLLECTION = "school_entry_qrs"
    }
}
