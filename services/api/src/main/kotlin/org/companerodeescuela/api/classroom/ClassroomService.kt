package org.companerodeescuela.api.classroom

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.ClassInvite
import org.companerodeescuela.shared.contracts.ClassroomMemberRole
import org.companerodeescuela.shared.contracts.ClassroomMembership
import org.companerodeescuela.shared.contracts.ClassroomStatus
import org.companerodeescuela.shared.contracts.ClassroomSummary
import org.companerodeescuela.shared.contracts.CreateClassInviteRequest
import org.companerodeescuela.shared.contracts.CreateClassroomRequest
import org.companerodeescuela.shared.contracts.JoinClassInviteResponse
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class ClassroomService(
    private val repository: ClassroomRepository,
    private val groupRepository: AcademicGroupRepository? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) {
    suspend fun listFor(actor: UserSummary): List<ClassroomSummary> {
        val admin = isAcademicAdmin(actor)
        return if (admin) {
            repository.listClassrooms().map { classroom ->
                classroom.toSummary(
                    canManage = true,
                    canManageEnrollment = true,
                )
            }
        } else {
            val direct = repository.listClassroomsForUser(actor.id)
                .map { (classroom, membership) ->
                    classroom.toSummary(
                        canManage = canOperate(actor, classroom),
                        canManageEnrollment = false,
                        joinedAtEpochSeconds = membership.joinedAt.epochSecond,
                    )
                }
            val groupIds = groupRepository
                ?.listGroupsForUser(actor.id)
                .orEmpty()
                .map(AcademicGroupRecord::id)
                .toSet()
            val inherited = repository.listClassroomsForGroups(groupIds)
                .map { classroom ->
                    classroom.toSummary(
                        canManage = canOperate(actor, classroom),
                        canManageEnrollment = false,
                    )
                }
            (direct + inherited).distinctBy(ClassroomSummary::id)
        }.sortedWith(
            compareBy<ClassroomSummary> { it.groupName.orEmpty() }
                .thenBy(ClassroomSummary::name),
        )
    }

    suspend fun create(
        actor: UserSummary,
        request: CreateClassroomRequest,
    ): ClassroomSummary {
        requireAcademicAdmin(actor)
        val name = request.name.trim()
        if (name.length !in 2..120) {
            throw ApiException.Validation("Classroom name must contain between 2 and 120 characters")
        }
        val groupId = required(request.groupId, "groupId")
        val groupName = required(request.groupName, "groupName")
        val teacherId = required(request.teacherId, "teacherId")
        val teacherDisplayName = required(request.teacherDisplayName, "teacherDisplayName")
        val now = clock.instant()
        val classroom = NativeClassroom(
            id = UUID.randomUUID().toString(),
            name = name,
            description = request.description?.trim()?.takeIf(String::isNotBlank)?.take(500),
            room = request.room?.trim()?.takeIf(String::isNotBlank)?.take(120),
            groupId = groupId,
            groupName = groupName,
            teacherId = teacherId,
            teacherDisplayName = teacherDisplayName,
            status = ClassroomStatus.ACTIVE,
            createdAt = now,
        )
        repository.createClassroom(classroom)
        repository.addMembershipIfAbsent(
            NativeClassroomMembership(
                classroomId = classroom.id,
                userId = teacherId,
                role = ClassroomMemberRole.TEACHER,
                joinedAt = now,
            ),
        )
        return classroom.toSummary(
            canManage = true,
            canManageEnrollment = true,
        )
    }

    suspend fun createInvite(
        actor: UserSummary,
        classroomId: String,
        request: CreateClassInviteRequest,
    ): ClassInvite {
        requireAcademicAdmin(actor)
        val classroom = repository.findClassroom(classroomId)
            ?: throw ApiException.NotFound("Classroom was not found")
        if (classroom.status != ClassroomStatus.ACTIVE) {
            throw ApiException.Conflict("Archived classrooms cannot create invitations")
        }
        if (request.ttlMinutes !in MIN_TTL_MINUTES..MAX_TTL_MINUTES) {
            throw ApiException.Validation("Invite duration must be between 5 and 60 minutes")
        }
        if (request.maxUses !in 1..MAX_INVITE_USES) {
            throw ApiException.Validation("Invite maxUses must be between 1 and 500")
        }

        val now = clock.instant()
        val rawCode = newInviteCode()
        val record = NativeClassInviteRecord(
            id = UUID.randomUUID().toString(),
            classroomId = classroomId,
            tokenHash = hashCode(rawCode),
            createdBy = actor.id,
            createdAt = now,
            expiresAt = now.plus(Duration.ofMinutes(request.ttlMinutes.toLong())),
            maxUses = request.maxUses,
        )
        repository.createInvite(record)
        return ClassInvite(
            id = record.id,
            classroomId = record.classroomId,
            code = rawCode,
            expiresAtEpochSeconds = record.expiresAt.epochSecond,
            maxUses = record.maxUses,
            uses = 0,
        )
    }

    suspend fun join(
        actor: UserSummary,
        rawCode: String,
    ): JoinClassInviteResponse {
        if (UserRole.STUDENT !in actor.roles) {
            throw ApiException.Forbidden("Only student accounts can join with an enrollment code")
        }
        val normalized = normalizeCode(rawCode)
        if (normalized.length != CODE_LENGTH) {
            throw ApiException.Validation("Invite code is invalid")
        }
        val invite = repository.findInviteByHash(hashCode(formatCode(normalized)))
            ?: throw ApiException.NotFound("Invite code was not found")
        val classroom = repository.findClassroom(invite.classroomId)
            ?: throw ApiException.NotFound("Classroom was not found")

        repository.findMembership(classroom.id, actor.id)?.let { existing ->
            return JoinClassInviteResponse(
                classroom = classroom.toSummary(
                    canManage = false,
                    canManageEnrollment = false,
                    joinedAtEpochSeconds = existing.joinedAt.epochSecond,
                ),
                membership = existing.toContract(),
            )
        }

        val now = clock.instant()
        val consumed = repository.consumeInvite(invite.id, now)
            ?: throw ApiException.Conflict("Invite code expired, was revoked, or reached its limit")
        val membership = repository.addMembershipIfAbsent(
            NativeClassroomMembership(
                classroomId = consumed.classroomId,
                userId = actor.id,
                role = ClassroomMemberRole.STUDENT,
                joinedAt = now,
            ),
        )
        return JoinClassInviteResponse(
            classroom = classroom.toSummary(
                canManage = false,
                canManageEnrollment = false,
                joinedAtEpochSeconds = membership.joinedAt.epochSecond,
            ),
            membership = membership.toContract(),
        )
    }

    suspend fun revokeInvite(
        actor: UserSummary,
        classroomId: String,
        inviteId: String,
    ) {
        requireAcademicAdmin(actor)
        if (!repository.revokeInvite(inviteId, classroomId, clock.instant())) {
            throw ApiException.NotFound("Active invitation was not found")
        }
    }

    suspend fun nativeChannelsFor(actor: UserSummary): List<ClassroomSummary> = listFor(actor)

    suspend fun requireCanRead(actor: UserSummary, classroomId: String): ClassroomSummary {
        val classroom = repository.findClassroom(classroomId)
            ?: throw ApiException.NotFound("Classroom was not found")
        val membership = repository.findMembership(classroomId, actor.id)
        val inheritedAccess = classroom.groupId?.let { groupId ->
            groupRepository?.isUserInGroup(actor.id, groupId)
        } == true
        if (membership == null && !inheritedAccess && !isAcademicAdmin(actor)) {
            throw ApiException.Forbidden("This classroom is not assigned to your account or academic group")
        }
        return classroom.toSummary(
            canManage = canOperate(actor, classroom),
            canManageEnrollment = isAcademicAdmin(actor),
            joinedAtEpochSeconds = membership?.joinedAt?.epochSecond,
        )
    }

    suspend fun requireCanPublish(actor: UserSummary, classroomId: String): ClassroomSummary {
        val classroom = repository.findClassroom(classroomId)
            ?: throw ApiException.NotFound("Classroom was not found")
        if (!canOperate(actor, classroom)) {
            throw ApiException.Forbidden("Only the assigned teacher or administrators can publish")
        }
        return classroom.toSummary(
            canManage = true,
            canManageEnrollment = isAcademicAdmin(actor),
        )
    }

    private fun canOperate(actor: UserSummary, classroom: NativeClassroom): Boolean =
        isAcademicAdmin(actor) ||
            (UserRole.TEACHER in actor.roles && classroom.teacherId == actor.id)

    private fun requireAcademicAdmin(actor: UserSummary) {
        if (!isAcademicAdmin(actor)) {
            throw ApiException.Forbidden("Academic administration permission is required")
        }
    }

    private fun isAcademicAdmin(actor: UserSummary): Boolean =
        UserRole.ADMIN in actor.roles || UserRole.SUPER_ADMIN in actor.roles

    private fun required(raw: String, name: String): String =
        raw.trim().takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("$name is required")

    private fun NativeClassroom.toSummary(
        canManage: Boolean,
        canManageEnrollment: Boolean,
        joinedAtEpochSeconds: Long? = null,
    ): ClassroomSummary = ClassroomSummary(
        id = id,
        name = name,
        description = description,
        room = room,
        groupId = groupId,
        groupName = groupName,
        teacherId = teacherId,
        teacherDisplayName = teacherDisplayName,
        status = status,
        canManage = canManage,
        canManageEnrollment = canManageEnrollment,
        joinedAtEpochSeconds = joinedAtEpochSeconds,
    )

    private fun NativeClassroomMembership.toContract(): ClassroomMembership =
        ClassroomMembership(
            classroomId = classroomId,
            userId = userId,
            role = role,
            joinedAtEpochSeconds = joinedAt.epochSecond,
        )

    private fun newInviteCode(): String {
        val chars = CharArray(CODE_LENGTH) {
            CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]
        }
        return formatCode(chars.concatToString())
    }

    private fun normalizeCode(code: String): String =
        code.uppercase().filter { it in CODE_ALPHABET }

    private fun formatCode(normalized: String): String =
        normalized.chunked(CODE_GROUP_SIZE).joinToString("-")

    private fun hashCode(code: String): String {
        val normalized = formatCode(normalizeCode(code))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()),
        )
    }

    private companion object {
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val CODE_LENGTH = 8
        const val CODE_GROUP_SIZE = 4
        const val MIN_TTL_MINUTES = 5
        const val MAX_TTL_MINUTES = 60
        const val MAX_INVITE_USES = 500
    }
}
