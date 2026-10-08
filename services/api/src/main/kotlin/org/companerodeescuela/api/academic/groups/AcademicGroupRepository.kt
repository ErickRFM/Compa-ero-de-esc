package org.companerodeescuela.api.academic.groups

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document

data class AcademicGroupRecord(
    val id: String,
    val name: String,
    val active: Boolean,
    val createdAt: Instant,
)

data class AcademicGroupMembershipRecord(
    val groupId: String,
    val userId: String,
    val joinedAt: Instant,
)

interface AcademicGroupRepository {
    suspend fun create(group: AcademicGroupRecord)
    suspend fun find(groupId: String): AcademicGroupRecord?
    suspend fun list(): List<AcademicGroupRecord>
    suspend fun assign(membership: AcademicGroupMembershipRecord): AcademicGroupMembershipRecord
    suspend fun listGroupsForUser(userId: String): List<AcademicGroupRecord>
    suspend fun isUserInGroup(userId: String, groupId: String): Boolean
    suspend fun listMembers(groupId: String): List<AcademicGroupMembershipRecord> = emptyList()
}

class InMemoryAcademicGroupRepository : AcademicGroupRepository {
    private val groups = ConcurrentHashMap<String, AcademicGroupRecord>()
    private val memberships = ConcurrentHashMap<String, AcademicGroupMembershipRecord>()

    override suspend fun create(group: AcademicGroupRecord) {
        groups[group.id] = group
    }

    override suspend fun find(groupId: String): AcademicGroupRecord? = groups[groupId]

    override suspend fun list(): List<AcademicGroupRecord> =
        groups.values.sortedBy(AcademicGroupRecord::name)

    override suspend fun assign(
        membership: AcademicGroupMembershipRecord,
    ): AcademicGroupMembershipRecord =
        memberships.putIfAbsent(key(membership.groupId, membership.userId), membership) ?: membership

    override suspend fun listGroupsForUser(userId: String): List<AcademicGroupRecord> =
        memberships.values
            .filter { it.userId == userId }
            .mapNotNull { groups[it.groupId] }
            .filter(AcademicGroupRecord::active)
            .sortedBy(AcademicGroupRecord::name)

    override suspend fun isUserInGroup(userId: String, groupId: String): Boolean =
        memberships.containsKey(key(groupId, userId))

    override suspend fun listMembers(groupId: String): List<AcademicGroupMembershipRecord> =
        memberships.values.filter { it.groupId == groupId }.sortedBy { it.userId }

    private fun key(groupId: String, userId: String): String = "$groupId::$userId"
}

class MongoAcademicGroupRepository(
    database: MongoDatabase,
) : AcademicGroupRepository {
    private val groups: MongoCollection<Document> = database.getCollection("academic_groups")
    private val memberships: MongoCollection<Document> =
        database.getCollection("academic_group_memberships")
    private val indexMutex = Mutex()

    @Volatile
    private var indexesReady = false

    override suspend fun create(group: AcademicGroupRecord) {
        ensureIndexes()
        groups.insertOne(
            Document()
                .append("_id", group.id)
                .append("name", group.name)
                .append("active", group.active)
                .append("createdAt", Date.from(group.createdAt)),
        )
    }

    override suspend fun find(groupId: String): AcademicGroupRecord? {
        ensureIndexes()
        return groups.find(eq("_id", groupId)).firstOrNull()?.toGroup()
    }

    override suspend fun list(): List<AcademicGroupRecord> {
        ensureIndexes()
        return groups.find().toList().map { it.toGroup() }.sortedBy(AcademicGroupRecord::name)
    }

    override suspend fun assign(
        membership: AcademicGroupMembershipRecord,
    ): AcademicGroupMembershipRecord {
        ensureIndexes()
        val filter = and(
            eq("groupId", membership.groupId),
            eq("userId", membership.userId),
        )
        memberships.find(filter).firstOrNull()?.let { return it.toMembership() }
        return try {
            memberships.insertOne(
                Document()
                    .append("groupId", membership.groupId)
                    .append("userId", membership.userId)
                    .append("joinedAt", Date.from(membership.joinedAt)),
            )
            membership
        } catch (error: com.mongodb.MongoWriteException) {
            memberships.find(filter).firstOrNull()?.toMembership() ?: throw error
        }
    }

    override suspend fun listGroupsForUser(userId: String): List<AcademicGroupRecord> {
        ensureIndexes()
        val ids = memberships.find(eq("userId", userId)).toList()
            .mapNotNull { it.getString("groupId") }
            .toSet()
        if (ids.isEmpty()) return emptyList()
        return groups.find().toList()
            .map { it.toGroup() }
            .filter { it.active && it.id in ids }
            .sortedBy(AcademicGroupRecord::name)
    }

    override suspend fun isUserInGroup(userId: String, groupId: String): Boolean {
        ensureIndexes()
        return memberships.find(and(eq("groupId", groupId), eq("userId", userId)))
            .firstOrNull() != null
    }

    override suspend fun listMembers(groupId: String): List<AcademicGroupMembershipRecord> {
        ensureIndexes()
        return memberships.find(eq("groupId", groupId)).toList()
            .map(Document::toMembership).sortedBy { it.userId }
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return@withLock
            memberships.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("groupId"),
                    Indexes.ascending("userId"),
                ),
                IndexOptions().unique(true).name("uq_academic_group_membership"),
            )
            memberships.createIndex(
                Indexes.ascending("userId"),
                IndexOptions().name("ix_academic_group_membership_user"),
            )
            indexesReady = true
        }
    }

    private fun Document.toGroup(): AcademicGroupRecord = AcademicGroupRecord(
        id = getString("_id"),
        name = getString("name"),
        active = getBoolean("active", true),
        createdAt = getDate("createdAt")?.toInstant() ?: Instant.EPOCH,
    )

    private fun Document.toMembership(): AcademicGroupMembershipRecord =
        AcademicGroupMembershipRecord(
            groupId = getString("groupId"),
            userId = getString("userId"),
            joinedAt = getDate("joinedAt")?.toInstant() ?: Instant.EPOCH,
        )
}
