package org.companerodeescuela.api.integrations.push

import com.mongodb.MongoException
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import java.util.Date
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MongoInstitutionalIdentityLinkRepository(
    database: MongoDatabase,
) : InstitutionalIdentityLinkRepository {
    private val links: MongoCollection<Document> = database.getCollection(COLLECTION)
    private val indexMutex = Mutex()

    @Volatile
    private var indexesReady = false

    override suspend fun findByExternalId(
        institutionId: String,
        entityType: InstitutionalIdentityKind,
        externalId: String,
    ): InstitutionalIdentityLink? {
        ensureIndexes()
        return links.find(identityFilter(institutionId, entityType, externalId)).firstOrNull()?.toRecord()
    }

    override suspend fun createIfAbsent(link: InstitutionalIdentityLink): InstitutionalIdentityLinkAcceptance {
        ensureIndexes()
        val byExternal = links.find(identityFilter(link.institutionId, link.entityType, link.externalId)).firstOrNull()
        if (byExternal != null) return byExternal.toRecord().accepts(link)
        val byNative = links.find(nativeFilter(link.institutionId, link.entityType, link.nativeUserId)).firstOrNull()
        if (byNative != null) return InstitutionalIdentityLinkAcceptance.CONFLICT
        return try {
            links.insertOne(link.toDocument())
            InstitutionalIdentityLinkAcceptance.CREATED
        } catch (error: MongoException) {
            if (error.code != DUPLICATE_KEY_CODE) throw error
            val racedExternal = links.find(identityFilter(link.institutionId, link.entityType, link.externalId)).firstOrNull()
            if (racedExternal != null) racedExternal.toRecord().accepts(link)
            else InstitutionalIdentityLinkAcceptance.CONFLICT
        }
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return
            links.createIndex(
                Indexes.ascending("institutionId", "entityType", "externalId"),
                IndexOptions().unique(true),
            )
            links.createIndex(
                Indexes.ascending("institutionId", "entityType", "nativeUserId"),
                IndexOptions().unique(true),
            )
            links.createIndex(
                Indexes.ascending("institutionId", "entityType", "operationId"),
                IndexOptions().unique(true),
            )
            indexesReady = true
        }
    }

    private fun identityFilter(
        institutionId: String,
        entityType: InstitutionalIdentityKind,
        externalId: String,
    ): Document =
        Document("institutionId", institutionId)
            .append("entityType", entityType.name)
            .append("externalId", externalId)

    private fun nativeFilter(
        institutionId: String,
        entityType: InstitutionalIdentityKind,
        nativeUserId: String,
    ): Document =
        Document("institutionId", institutionId)
            .append("entityType", entityType.name)
            .append("nativeUserId", nativeUserId)

    private fun InstitutionalIdentityLink.toDocument(): Document =
        Document("institutionId", institutionId)
            .append("entityType", entityType.name)
            .append("externalId", externalId)
            .append("nativeUserId", nativeUserId)
            .append("linkedBy", linkedBy)
            .append("operationId", operationId)
            .append("payloadDigest", payloadDigest)
            .append("linkedAt", Date.from(linkedAt))

    private fun Document.toRecord(): InstitutionalIdentityLink =
        InstitutionalIdentityLink(
            institutionId = getString("institutionId"),
            entityType = InstitutionalIdentityKind.valueOf(getString("entityType")),
            externalId = getString("externalId"),
            nativeUserId = getString("nativeUserId"),
            linkedBy = getString("linkedBy"),
            operationId = getString("operationId"),
            payloadDigest = getString("payloadDigest"),
            linkedAt = getDate("linkedAt").toInstant(),
        )

    private fun InstitutionalIdentityLink.accepts(
        other: InstitutionalIdentityLink,
    ): InstitutionalIdentityLinkAcceptance =
        if (nativeUserId == other.nativeUserId &&
            linkedBy == other.linkedBy &&
            operationId == other.operationId &&
            payloadDigest == other.payloadDigest
        ) {
            InstitutionalIdentityLinkAcceptance.DUPLICATE
        } else {
            InstitutionalIdentityLinkAcceptance.CONFLICT
        }

    private companion object {
        const val COLLECTION = "institutional_identity_links"
        const val DUPLICATE_KEY_CODE = 11000
    }
}
