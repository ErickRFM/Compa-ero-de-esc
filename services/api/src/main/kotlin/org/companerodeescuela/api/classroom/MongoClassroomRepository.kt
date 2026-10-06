package org.companerodeescuela.api.classroom

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.Document
import org.companerodeescuela.shared.contracts.ClassroomMemberRole
import org.companerodeescuela.shared.contracts.ClassroomStatus

class MongoClassroomRepository(database: MongoDatabase) : ClassroomRepository {
    private val classrooms: MongoCollection<Document> = database.getCollection(CLASSROOMS)
    private val memberships: MongoCollection<Document> = database.getCollection(MEMBERSHIPS)
    private val invites: MongoCollection<Document> = database.getCollection(INVITES)
    private val indexMutex = Mutex()

    @Volatile
    private var indexesReady = false

    override suspend fun createClassroom(classroom: NativeClassroom) {
        ensureIndexes()
        classrooms.insertOne(classroom.toDocument())
    }

    override suspend fun findClassroom(classroomId: String): NativeClassroom? {
        ensureIndexes()
        return classrooms.find(eq("_id", classroomId)).firstOrNull()?.toClassroom()
    }

    override suspend fun listClassrooms(): List<NativeClassroom> {
        ensureIndexes()
        return classrooms.find().toList().map { it.toClassroom() }
    }

    override suspend fun listClassroomsForGroups(groupIds: Set<String>): List<NativeClassroom> {
        ensureIndexes()
        if (groupIds.isEmpty()) return emptyList()
        return classrooms.find(com.mongodb.client.model.Filters.`in`("groupId", groupIds))
            .toList()
            .map { it.toClassroom() }
    }

    override suspend fun listClassroomsForUser(
        userId: String,
    ): List<Pair<NativeClassroom, NativeClassroomMembership>> {
        ensureIndexes()
        val userMemberships = memberships.find(eq("userId", userId)).toList()
            .map { it.toMembership() }
        return userMemberships.mapNotNull { membership ->
            findClassroom(membership.classroomId)?.let { it to membership }
        }
    }

    override suspend fun findMembership(
        classroomId: String,
        userId: String,
    ): NativeClassroomMembership? {
        ensureIndexes()
        return memberships.find(
            and(eq("classroomId", classroomId), eq("userId", userId)),
        ).firstOrNull()?.toMembership()
    }

    override suspend fun addMembershipIfAbsent(
        membership: NativeClassroomMembership,
    ): NativeClassroomMembership {
        ensureIndexes()
        val filter = and(
            eq("classroomId", membership.classroomId),
            eq("userId", membership.userId),
        )
        val existing = memberships.find(filter).firstOrNull()?.toMembership()
        if (existing != null) return existing
        return try {
            memberships.insertOne(membership.toDocument())
            membership
        } catch (error: com.mongodb.MongoWriteException) {
            memberships.find(filter).firstOrNull()?.toMembership() ?: throw error
        }
    }

    override suspend fun createInvite(invite: NativeClassInviteRecord) {
        ensureIndexes()
        invites.insertOne(invite.toDocument())
    }

    override suspend fun findInviteByHash(tokenHash: String): NativeClassInviteRecord? {
        ensureIndexes()
        return invites.find(eq("tokenHash", tokenHash)).firstOrNull()?.toInvite()
    }

    override suspend fun consumeInvite(
        inviteId: String,
        now: Instant,
    ): NativeClassInviteRecord? {
        ensureIndexes()
        val current = invites.find(eq("_id", inviteId)).firstOrNull()?.toInvite() ?: return null
        if (
            current.revokedAt != null ||
            current.expiresAt <= now ||
            current.uses >= current.maxUses
        ) return null
        val document = invites.findOneAndUpdate(
            and(
                eq("_id", inviteId),
                eq("revokedAt", null),
                gt("expiresAt", Date.from(now)),
                eq("uses", current.uses),
            ),
            Updates.inc("uses", 1),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        ) ?: return null
        return document.toInvite()
    }

    override suspend fun revokeInvite(
        inviteId: String,
        classroomId: String,
        now: Instant,
    ): Boolean {
        ensureIndexes()
        val result = invites.updateOne(
            and(
                eq("_id", inviteId),
                eq("classroomId", classroomId),
                eq("revokedAt", null),
            ),
            Updates.set("revokedAt", Date.from(now)),
        )
        return result.modifiedCount > 0L
    }

    private suspend fun ensureIndexes() {
        if (indexesReady) return
        indexMutex.withLock {
            if (indexesReady) return@withLock
            memberships.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending("classroomId"),
                    Indexes.ascending("userId"),
                ),
                IndexOptions().unique(true).name("uq_classroom_membership"),
            )
            memberships.createIndex(
                Indexes.ascending("userId"),
                IndexOptions().name("ix_classroom_membership_user"),
            )
            classrooms.createIndex(
                Indexes.ascending("groupId"),
                IndexOptions().name("ix_classroom_group"),
            )
            classrooms.createIndex(
                Indexes.ascending("teacherId"),
                IndexOptions().name("ix_classroom_teacher"),
            )
            invites.createIndex(
                Indexes.ascending("tokenHash"),
                IndexOptions().unique(true).name("uq_classroom_invite_token"),
            )
            indexesReady = true
        }
    }

    private fun NativeClassroom.toDocument(): Document = Document()
        .append("_id", id)
        .append("name", name)
        .append("description", description)
        .append("room", room)
        .append("groupId", groupId)
        .append("groupName", groupName)
        .append("teacherId", teacherId)
        .append("teacherDisplayName", teacherDisplayName)
        .append("status", status.name)
        .append("createdAt", Date.from(createdAt))

    private fun Document.toClassroom(): NativeClassroom = NativeClassroom(
        id = getString("_id"),
        name = getString("name"),
        description = getString("description"),
        room = getString("room"),
        groupId = getString("groupId"),
        groupName = getString("groupName"),
        teacherId = getString("teacherId"),
        teacherDisplayName = getString("teacherDisplayName"),
        status = ClassroomStatus.valueOf(getString("status")),
        createdAt = getDate("createdAt").toInstant(),
    )

    private fun NativeClassroomMembership.toDocument(): Document = Document()
        .append("classroomId", classroomId)
        .append("userId", userId)
        .append("role", role.name)
        .append("joinedAt", Date.from(joinedAt))

    private fun Document.toMembership(): NativeClassroomMembership = NativeClassroomMembership(
        classroomId = getString("classroomId"),
        userId = getString("userId"),
        role = ClassroomMemberRole.valueOf(getString("role")),
        joinedAt = getDate("joinedAt").toInstant(),
    )

    private fun NativeClassInviteRecord.toDocument(): Document = Document()
        .append("_id", id)
        .append("classroomId", classroomId)
        .append("tokenHash", tokenHash)
        .append("createdBy", createdBy)
        .append("createdAt", Date.from(createdAt))
        .append("expiresAt", Date.from(expiresAt))
        .append("maxUses", maxUses)
        .append("uses", uses)
        .append("revokedAt", revokedAt?.let(Date::from))

    private fun Document.toInvite(): NativeClassInviteRecord = NativeClassInviteRecord(
        id = getString("_id"),
        classroomId = getString("classroomId"),
        tokenHash = getString("tokenHash"),
        createdBy = getString("createdBy"),
        createdAt = getDate("createdAt").toInstant(),
        expiresAt = getDate("expiresAt").toInstant(),
        maxUses = (get("maxUses") as Number).toInt(),
        uses = (get("uses") as Number).toInt(),
        revokedAt = getDate("revokedAt")?.toInstant(),
    )

    private companion object {
        const val CLASSROOMS = "native_classrooms"
        const val MEMBERSHIPS = "native_classroom_memberships"
        const val INVITES = "native_class_invites"
    }
}
