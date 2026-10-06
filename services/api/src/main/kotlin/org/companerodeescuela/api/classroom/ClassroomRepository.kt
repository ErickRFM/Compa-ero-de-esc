package org.companerodeescuela.api.classroom

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.companerodeescuela.shared.contracts.ClassroomMemberRole
import org.companerodeescuela.shared.contracts.ClassroomStatus

data class NativeClassroom(
    val id: String,
    val name: String,
    val description: String?,
    val room: String?,
    val groupId: String?,
    val groupName: String?,
    val teacherId: String,
    val teacherDisplayName: String,
    val status: ClassroomStatus,
    val createdAt: Instant,
)

data class NativeClassroomMembership(
    val classroomId: String,
    val userId: String,
    val role: ClassroomMemberRole,
    val joinedAt: Instant,
)

data class NativeClassInviteRecord(
    val id: String,
    val classroomId: String,
    val tokenHash: String,
    val createdBy: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val maxUses: Int,
    val uses: Int = 0,
    val revokedAt: Instant? = null,
)

interface ClassroomRepository {
    suspend fun createClassroom(classroom: NativeClassroom)
    suspend fun findClassroom(classroomId: String): NativeClassroom?
    suspend fun listClassrooms(): List<NativeClassroom>
    suspend fun listClassroomsForUser(userId: String): List<Pair<NativeClassroom, NativeClassroomMembership>>
    suspend fun findMembership(classroomId: String, userId: String): NativeClassroomMembership?
    suspend fun addMembershipIfAbsent(membership: NativeClassroomMembership): NativeClassroomMembership
    suspend fun createInvite(invite: NativeClassInviteRecord)
    suspend fun findInviteByHash(tokenHash: String): NativeClassInviteRecord?
    suspend fun consumeInvite(inviteId: String, now: Instant): NativeClassInviteRecord?
    suspend fun revokeInvite(inviteId: String, classroomId: String, now: Instant): Boolean
}

class InMemoryClassroomRepository : ClassroomRepository {
    private val classrooms = ConcurrentHashMap<String, NativeClassroom>()
    private val memberships = ConcurrentHashMap<String, NativeClassroomMembership>()
    private val invites = ConcurrentHashMap<String, NativeClassInviteRecord>()

    override suspend fun createClassroom(classroom: NativeClassroom) {
        classrooms[classroom.id] = classroom
    }

    override suspend fun findClassroom(classroomId: String): NativeClassroom? = classrooms[classroomId]

    override suspend fun listClassrooms(): List<NativeClassroom> = classrooms.values.toList()

    override suspend fun listClassroomsForUser(
        userId: String,
    ): List<Pair<NativeClassroom, NativeClassroomMembership>> =
        memberships.values
            .filter { it.userId == userId }
            .mapNotNull { membership ->
                classrooms[membership.classroomId]?.let { it to membership }
            }

    override suspend fun findMembership(
        classroomId: String,
        userId: String,
    ): NativeClassroomMembership? = memberships[membershipKey(classroomId, userId)]

    override suspend fun addMembershipIfAbsent(
        membership: NativeClassroomMembership,
    ): NativeClassroomMembership =
        memberships.putIfAbsent(
            membershipKey(membership.classroomId, membership.userId),
            membership,
        ) ?: membership

    override suspend fun createInvite(invite: NativeClassInviteRecord) {
        invites[invite.id] = invite
    }

    override suspend fun findInviteByHash(tokenHash: String): NativeClassInviteRecord? =
        invites.values.firstOrNull { it.tokenHash == tokenHash }

    override suspend fun consumeInvite(inviteId: String, now: Instant): NativeClassInviteRecord? =
        synchronized(invites) {
            val current = invites[inviteId] ?: return@synchronized null
            if (
                current.revokedAt != null ||
                current.expiresAt <= now ||
                current.uses >= current.maxUses
            ) return@synchronized null
            val updated = current.copy(uses = current.uses + 1)
            invites[inviteId] = updated
            updated
        }

    override suspend fun revokeInvite(
        inviteId: String,
        classroomId: String,
        now: Instant,
    ): Boolean = synchronized(invites) {
        val current = invites[inviteId] ?: return@synchronized false
        if (current.classroomId != classroomId || current.revokedAt != null) {
            return@synchronized false
        }
        invites[inviteId] = current.copy(revokedAt = now)
        true
    }

    private fun membershipKey(classroomId: String, userId: String): String =
        "$classroomId::$userId"
}
