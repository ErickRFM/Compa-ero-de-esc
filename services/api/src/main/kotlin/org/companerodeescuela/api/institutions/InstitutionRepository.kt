package org.companerodeescuela.api.institutions

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.companerodeescuela.shared.contracts.InstitutionSummary

interface InstitutionRepository {
    suspend fun findEnabled(id: String): InstitutionSummary?
    suspend fun listEnabled(): List<InstitutionSummary>
}
/** Empty unless explicitly populated by a test or authorized local setup; no demo seed. */
class InMemoryInstitutionRepository(initial: List<InstitutionSummary> = emptyList()) : InstitutionRepository {
    private val directory = initial.associateBy { it.id }.toMap()
    override suspend fun findEnabled(id: String): InstitutionSummary? = directory[id]
    override suspend fun listEnabled(): List<InstitutionSummary> = directory.values.sortedBy { it.id }.take(100)
}
class MongoInstitutionRepository(database: MongoDatabase) : InstitutionRepository {
    private val directory = database.getCollection<Document>("platform_institutions")
    override suspend fun findEnabled(id: String): InstitutionSummary? = directory.find(
        com.mongodb.client.model.Filters.and(eq("_id", id), eq("registrationEnabled", true)))
        .firstOrNull()?.summary()
    override suspend fun listEnabled(): List<InstitutionSummary> = directory.find(eq("registrationEnabled", true))
        .sort(ascending("_id")).limit(100).toList().mapNotNull { it.summary() }
    private fun Document.summary(): InstitutionSummary? {
        val id = getString("_id")?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
        val name = getString("displayName")?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
        return InstitutionSummary(id, name)
    }
}
