package org.companerodeescuela.api.academic.groups

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.exists
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
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
    val institutionId: String? = null,
    val periodId: String? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val scopeRevision: Long = 0,
    val scopeAudit: List<AcademicGroupScopeAudit> = emptyList(),
)

data class AcademicGroupScopeAudit(val operationId:String,val actorId:String,val fromRevision:Long,val toRevision:Long,
    val institutionBefore:String?,val institutionAfter:String,val periodBefore:String?,val periodAfter:String,
    val startsAt:Instant,val endsAt:Instant,val occurredAt:Instant,val payloadDigest:String)
data class AcademicGroupScopeChange(val operationId:String,val actorId:String,val institutionId:String,val periodId:String,
    val startsAt:Instant,val endsAt:Instant,val occurredAt:Instant,val payloadDigest:String)

data class AcademicGroupMembershipRecord(
    val groupId: String,
    val userId: String,
    val joinedAt: Instant,
    val assignedBy:String?=null,
    val operationId:String?=null,
    val payloadDigest:String?=null,
    val groupScopeRevision:Long=-1,
)

interface AcademicGroupRepository {
    suspend fun create(group: AcademicGroupRecord)
    suspend fun find(groupId: String): AcademicGroupRecord?
    suspend fun list(): List<AcademicGroupRecord>
    suspend fun changeScope(groupId:String,expectedRevision:Long,change:AcademicGroupScopeChange):AcademicGroupRecord? = null
    suspend fun assign(membership: AcademicGroupMembershipRecord): AcademicGroupMembershipRecord
    suspend fun listGroupsForUser(userId: String): List<AcademicGroupRecord>
    suspend fun isUserInGroup(userId: String, groupId: String): Boolean
    suspend fun memberIdsForGroup(groupId: String): List<String>
}

class InMemoryAcademicGroupRepository : AcademicGroupRepository {
    private val scopeMutex=Mutex()
    private val groups = ConcurrentHashMap<String, AcademicGroupRecord>()
    private val memberships = ConcurrentHashMap<String, AcademicGroupMembershipRecord>()

    override suspend fun create(group: AcademicGroupRecord) {
        groups.putIfAbsent(group.id,group)?.let { it.requireExactCreation(group) }
    }

    override suspend fun find(groupId: String): AcademicGroupRecord? = groups[groupId]

    override suspend fun list(): List<AcademicGroupRecord> =
        groups.values.sortedBy(AcademicGroupRecord::name)

    override suspend fun changeScope(groupId:String,expectedRevision:Long,change:AcademicGroupScopeChange):AcademicGroupRecord? = scopeMutex.withLock {
        val current=groups[groupId] ?: return@withLock null
        if(current.exactScopeRetry(expectedRevision,change)) return@withLock current
        val updated=current.applyScopeChange(expectedRevision,change) ?: return@withLock null
        groups[groupId]=updated
        updated
    }

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

    override suspend fun memberIdsForGroup(groupId: String): List<String> =
        memberships.values.filter { it.groupId == groupId }.map { it.userId }.distinct().sorted()

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
        try { groups.insertOne(
            Document()
                .append("_id", group.id)
                .append("name", group.name)
                .append("active", group.active)
                .append("createdAt", Date.from(group.createdAt))
                .append("institutionId", group.institutionId).append("periodId", group.periodId)
                .append("startsAt", group.startsAt?.let(Date::from)).append("endsAt", group.endsAt?.let(Date::from))
                .append("scopeRevision",group.scopeRevision).append("scopeAudit",group.scopeAudit.map { it.toDocument() }),
        ) } catch(error:com.mongodb.MongoWriteException) {
            if(error.error.code!=11000) throw error
            (find(group.id) ?: throw error).requireExactCreation(group)
        }
    }

    override suspend fun find(groupId: String): AcademicGroupRecord? {
        ensureIndexes()
        return groups.find(eq("_id", groupId)).firstOrNull()?.toGroup()
    }

    override suspend fun list(): List<AcademicGroupRecord> {
        ensureIndexes()
        return groups.find().toList().map { it.toGroup() }.sortedBy(AcademicGroupRecord::name)
    }

    override suspend fun changeScope(groupId:String,expectedRevision:Long,change:AcademicGroupScopeChange):AcademicGroupRecord? {
        ensureIndexes()
        val current=find(groupId) ?: return null
        if(current.exactScopeRetry(expectedRevision,change)) return current
        val updated=current.applyScopeChange(expectedRevision,change) ?: return null
        val revisionFilter=if(expectedRevision==0L) or(eq("scopeRevision",0L),exists("scopeRevision",false)) else eq("scopeRevision",expectedRevision)
        val result=groups.findOneAndUpdate(and(eq("_id",groupId),revisionFilter),Updates.combine(
            Updates.set("institutionId",updated.institutionId),Updates.set("periodId",updated.periodId),
            Updates.set("startsAt",Date.from(updated.startsAt!!)),Updates.set("endsAt",Date.from(updated.endsAt!!)),
            Updates.set("scopeRevision",updated.scopeRevision),Updates.set("scopeAudit",updated.scopeAudit.map { it.toDocument() })),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER))
        if(result!=null) return result.toGroup()
        return find(groupId)?.takeIf { it.exactScopeRetry(expectedRevision,change) }
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
                    .append("joinedAt", Date.from(membership.joinedAt))
                    .append("assignedBy",membership.assignedBy).append("operationId",membership.operationId).append("payloadDigest",membership.payloadDigest).append("groupScopeRevision",membership.groupScopeRevision),
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

    override suspend fun memberIdsForGroup(groupId: String): List<String> {
        ensureIndexes()
        return memberships.find(eq("groupId", groupId)).toList()
            .mapNotNull { it.getString("userId") }.distinct().sorted()
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
        active = get("active") == true,
        createdAt = getDate("createdAt")?.toInstant() ?: Instant.EPOCH,
        institutionId = getString("institutionId"), periodId = getString("periodId"),
        startsAt = (get("startsAt") as? Date)?.toInstant(), endsAt = (get("endsAt") as? Date)?.toInstant(),
        scopeRevision = if(readScopeAudit()==null) -1 else when(val revision=get("scopeRevision")) { null->0;is Long->revision;is Int->revision.toLong();else-> -1L },
        scopeAudit = readScopeAudit() ?: emptyList(),
    )
    private fun AcademicGroupScopeAudit.toDocument()=Document("operationId",operationId).append("actorId",actorId)
        .append("fromRevision",fromRevision).append("toRevision",toRevision).append("institutionBefore",institutionBefore)
        .append("institutionAfter",institutionAfter).append("periodBefore",periodBefore).append("periodAfter",periodAfter)
        .append("startsAt",Date.from(startsAt)).append("endsAt",Date.from(endsAt)).append("occurredAt",Date.from(occurredAt)).append("payloadDigest",payloadDigest)
    private fun Document.readScopeAudit():List<AcademicGroupScopeAudit>? {
        val raw=get("scopeAudit") ?: return emptyList()
        if(raw !is List<*> || raw.size>1000) return null
        return try { raw.map { item ->
            val entry=item as? Document ?: return null
            fun text(key:String)=entry.get(key) as? String ?: error("Invalid audit")
            fun revision(key:String)=when(val value=entry.get(key)) { is Long->value;is Int->value.toLong();else->error("Invalid revision") }
            fun instant(key:String)=(entry.get(key) as? Date)?.toInstant() ?: error("Invalid time")
            AcademicGroupScopeAudit(text("operationId"),text("actorId"),revision("fromRevision"),revision("toRevision"),
                entry.get("institutionBefore") as? String,text("institutionAfter"),entry.get("periodBefore") as? String,text("periodAfter"),
                instant("startsAt"),instant("endsAt"),instant("occurredAt"),text("payloadDigest"))
        } } catch(_:IllegalStateException) { null }
    }

    private fun Document.toMembership(): AcademicGroupMembershipRecord =
        AcademicGroupMembershipRecord(
            groupId = getString("groupId"),
            userId = getString("userId"),
            joinedAt = getDate("joinedAt")?.toInstant() ?: Instant.EPOCH,
            assignedBy=getString("assignedBy"),operationId=getString("operationId"),payloadDigest=getString("payloadDigest"),
            groupScopeRevision=when(val revision=get("groupScopeRevision")) { is Long->revision;is Int->revision.toLong();else-> -1L },
        )
}

private fun AcademicGroupRecord.requireExactCreation(request:AcademicGroupRecord) {
    if(id!=request.id || name!=request.name || active!=request.active || institutionId!=request.institutionId || periodId!=request.periodId ||
        startsAt!=request.startsAt || endsAt!=request.endsAt || scopeRevision!=request.scopeRevision ||
        scopeAudit.map { it.copy(occurredAt=Instant.EPOCH) }!=request.scopeAudit.map { it.copy(occurredAt=Instant.EPOCH) })
        throw org.companerodeescuela.api.errors.ApiException.Conflict("Academic group already belongs to a different creation or scope")
}
