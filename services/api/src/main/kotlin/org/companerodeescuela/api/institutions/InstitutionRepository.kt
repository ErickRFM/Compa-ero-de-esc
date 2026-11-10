package org.companerodeescuela.api.institutions

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.shared.contracts.InstitutionSummary
import java.time.Instant
import java.util.Date

interface InstitutionRepository {
    suspend fun findEnabled(id: String): InstitutionSummary?

    /** Internal existence check. It does not open public registration. */
    suspend fun findConfigured(id: String): InstitutionSummary? = findEnabled(id)

    suspend fun listEnabled(): List<InstitutionSummary>

    /** Authorized owner-only provisioning; never called by public registration. */
    suspend fun createEnabled(
        institution: InstitutionSummary,
        actorId: String,
        operationId: String,
        now: Instant,
    ): Boolean = false
}

/** Empty unless explicitly populated by a test or authorized local setup; no demo seed. */
class InMemoryInstitutionRepository(
    initial: List<InstitutionSummary> = emptyList(),
) : InstitutionRepository {
    private val mutex = Mutex()
    private val directory = initial.associateBy { it.id }.toMutableMap()

    override suspend fun findEnabled(id: String): InstitutionSummary? = mutex.withLock { directory[id] }

    override suspend fun listEnabled(): List<InstitutionSummary> = mutex.withLock { directory.values.sortedBy { it.id }.take(100) }

    override suspend fun createEnabled(
        institution: InstitutionSummary,
        actorId: String,
        operationId: String,
        now: Instant,
    ): Boolean =
        mutex.withLock {
            if (institution.id in directory) return@withLock false
            directory[institution.id] = institution
            true
        }
}

class MongoInstitutionRepository(
    database: MongoDatabase,
) : InstitutionRepository {
    private val directory = database.getCollection<Document>("platform_institutions")

    override suspend fun createEnabled(
        institution: InstitutionSummary,
        actorId: String,
        operationId: String,
        now: Instant,
    ): Boolean =
        try {
            directory.insertOne(
                Document("_id", institution.id)
                    .append("displayName", institution.displayName)
                    .append("registrationEnabled", true)
                    .append("createdBy", actorId)
                    .append("createdOperationId", operationId)
                    .append("createdAt", Date.from(now)),
            )
            true
        } catch (error: MongoWriteException) {
            if (error.error.code == 11000) false else throw error
        }

    override suspend fun findConfigured(id: String): InstitutionSummary? = directory.find(eq("_id", id)).firstOrNull()?.summary()

    override suspend fun findEnabled(id: String): InstitutionSummary? {
        val filter = Filters.and(eq("_id", id), eq("registrationEnabled", true))
        return directory.find(filter).firstOrNull()?.summary()
    }

    override suspend fun listEnabled(): List<InstitutionSummary> {
        val rows =
            directory
                .find(eq("registrationEnabled", true))
                .sort(ascending("_id"))
                .limit(100)
                .toList()
        return rows.mapNotNull { it.summary() }
    }

    private fun Document.summary(): InstitutionSummary? {
        val id = getString("_id")?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
        val name = getString("displayName")?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
        return InstitutionSummary(id, name)
    }
}
