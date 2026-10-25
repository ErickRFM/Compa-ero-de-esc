package org.companerodeescuela.api.integrations.push

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.companerodeescuela.shared.contracts.InstitutionalIdentityKind
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class InstitutionalIdentityLink(
    val institutionId: String,
    val entityType: InstitutionalIdentityKind,
    val externalId: String,
    val nativeUserId: String,
    val linkedBy: String,
    val operationId: String,
    val payloadDigest: String,
    val linkedAt: Instant,
)

enum class InstitutionalIdentityLinkAcceptance {
    CREATED,
    DUPLICATE,
    CONFLICT,
}

interface InstitutionalIdentityLinkRepository {
    suspend fun findByNativeUserId(
        institutionId: String,
        entityType: InstitutionalIdentityKind,
        nativeUserId: String,
    ): InstitutionalIdentityLink? = null

    suspend fun findByExternalId(
        institutionId: String,
        entityType: InstitutionalIdentityKind,
        externalId: String,
    ): InstitutionalIdentityLink?

    suspend fun createIfAbsent(link: InstitutionalIdentityLink): InstitutionalIdentityLinkAcceptance
}

class InMemoryInstitutionalIdentityLinkRepository : InstitutionalIdentityLinkRepository {
    private val mutex = Mutex()
    private val links = ConcurrentHashMap<String, InstitutionalIdentityLink>()

    override suspend fun findByNativeUserId(
        institutionId: String,
        entityType: InstitutionalIdentityKind,
        nativeUserId: String,
    ): InstitutionalIdentityLink? =
        links.values.singleOrNull {
            it.institutionId == institutionId && it.entityType == entityType && it.nativeUserId == nativeUserId
        }

    override suspend fun findByExternalId(
        institutionId: String,
        entityType: InstitutionalIdentityKind,
        externalId: String,
    ): InstitutionalIdentityLink? = links[key(institutionId, entityType, externalId)]

    override suspend fun createIfAbsent(link: InstitutionalIdentityLink): InstitutionalIdentityLinkAcceptance =
        mutex.withLock {
            val identityKey = key(link.institutionId, link.entityType, link.externalId)
            val current = links[identityKey]
            if (current != null) {
                return@withLock if (current.sameCommand(link)) {
                    InstitutionalIdentityLinkAcceptance.DUPLICATE
                } else {
                    InstitutionalIdentityLinkAcceptance.CONFLICT
                }
            }

            val accountAlreadyLinked =
                links.values.any {
                    it.institutionId == link.institutionId &&
                        it.entityType == link.entityType &&
                        it.nativeUserId == link.nativeUserId
                }
            if (accountAlreadyLinked) return@withLock InstitutionalIdentityLinkAcceptance.CONFLICT

            val operationAlreadyUsed =
                links.values.any {
                    it.institutionId == link.institutionId &&
                        it.entityType == link.entityType &&
                        it.operationId == link.operationId
                }
            if (operationAlreadyUsed) return@withLock InstitutionalIdentityLinkAcceptance.CONFLICT

            links[identityKey] = link
            InstitutionalIdentityLinkAcceptance.CREATED
        }

    private fun key(
        institutionId: String,
        entityType: InstitutionalIdentityKind,
        externalId: String,
    ) = "$institutionId\u0000${entityType.name}\u0000$externalId"

    private fun InstitutionalIdentityLink.sameCommand(other: InstitutionalIdentityLink): Boolean =
        nativeUserId == other.nativeUserId &&
            linkedBy == other.linkedBy &&
            operationId == other.operationId &&
            payloadDigest == other.payloadDigest
}
