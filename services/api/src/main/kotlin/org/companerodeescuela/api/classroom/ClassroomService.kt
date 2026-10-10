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
    private val accounts:org.companerodeescuela.api.auth.PlatformAccountRepository?=null,
    private val mfa:org.companerodeescuela.api.security.MfaService?=null,
) {
    suspend fun listFor(actor:UserSummary):List<ClassroomSummary> {
        val current=policy().live(actor)
        val admin=isAcademicAdmin(actor)
        if(admin) policy().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,current.institutionId ?: "global")
        val direct=if(admin) repository.listClassrooms() else {
            val groupIds=groupRepository?.listGroupsForUser(actor.id).orEmpty().filter { it.institutionId==current.institutionId }.map { it.id }.toSet()
            (repository.listClassroomsForUser(actor.id).map { it.first }+repository.listClassroomsForGroups(groupIds)).distinctBy { it.id }
        }
        return direct.filter { classroom ->
            val group=classroom.groupId?.let { groupRepository?.find(it) }
            group!=null && validScope(classroom,group) && (UserRole.SUPER_ADMIN in current.roles || group.institutionId==current.institutionId) &&
                (admin || (UserRole.STUDENT in current.roles) || (UserRole.TEACHER in current.roles && classroom.teacherId==current.id && classroom.teacherRoleGeneration==current.roleGenerations[UserRole.TEACHER]))
        }.map { classroom -> classroom.toSummary(canManage=canOperate(actor,classroom),canManageEnrollment=admin,
            joinedAtEpochSeconds=repository.findMembership(classroom.id,actor.id)?.joinedAt?.epochSecond) }
            .sortedWith(compareBy<ClassroomSummary> { it.groupName.orEmpty() }.thenBy { it.name })
    }
    suspend fun create(actor:UserSummary,request:CreateClassroomRequest):ClassroomSummary {
        requireAcademicAdmin(actor)
        val groupId=required(request.groupId,"groupId")
        val group=groupRepository?.find(groupId) ?: throw ApiException.Validation("An actual academic group is required")
        val institution=group.institutionId ?: throw ApiException.Forbidden("Explicit group institution is required")
        val current=policy().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,institution)
        val operation=request.operationId ?: throw ApiException.Forbidden("Scoped MFA operation is required")
        val proof=request.proof ?: throw ApiException.Forbidden("Scoped MFA proof is required")
        if(!boundedId(operation)) throw ApiException.Validation("A bounded operation is required")
        if(!group.active || group.scopeRevision<0 || group.scopeRevision!=request.groupScopeRevision || group.startsAt==null || group.endsAt==null || group.endsAt<=clock.instant() || group.periodId.isNullOrBlank()) throw ApiException.Validation("Current group scope is required")
        val digest=org.companerodeescuela.shared.contracts.PrivilegedCommandDigests.classroomAssignment(groupId,request)
        (mfa ?: throw ApiException.DependencyUnavailable("MFA is unavailable")).requireStepUp(current,proof,org.companerodeescuela.shared.contracts.StepUpAction.MANAGE_ACADEMICS,groupId,operation,digest)
        val teacher=accounts?.findById(required(request.teacherId,"teacherId")) ?: throw ApiException.Validation("An actual verified teacher is required")
        if(!teacher.permitsSession || teacher.accountStatus!=org.companerodeescuela.shared.contracts.AccountStatus.ACTIVE || teacher.emailVerifiedAt==null || teacher.emailVerifiedAt>clock.instant() || UserRole.TEACHER !in teacher.roles || teacher.institutionId!=institution) throw ApiException.Validation("Teacher must be verified and active in this institution")
        val name=request.name.trim()
        if(name.length !in 2..120 || name.any(Char::isISOControl)) throw ApiException.Validation("Classroom name must contain between 2 and 120 characters")
        val now=clock.instant()
        val classroom=NativeClassroom(id="class-"+UUID.nameUUIDFromBytes((current.id+"\u0000"+operation).toByteArray(Charsets.UTF_8)),name=name,
            description=request.description?.trim()?.takeIf(String::isNotBlank)?.take(500),room=request.room?.trim()?.takeIf(String::isNotBlank)?.take(120),
            groupId=group.id,groupName=group.name,teacherId=teacher.id,teacherDisplayName=teacher.displayName,status=ClassroomStatus.ACTIVE,createdAt=now,
            groupScopeRevision=group.scopeRevision,teacherRoleGeneration=teacher.roleGenerations[UserRole.TEACHER] ?: throw ApiException.Validation("Teacher grant generation is required"),
            assignedBy=current.id,assignmentOperationId=operation,assignmentDigest=digest)
        repository.createClassroom(classroom)
        repository.addMembershipIfAbsent(NativeClassroomMembership(classroom.id,teacher.id,ClassroomMemberRole.TEACHER,now))
        return (repository.findClassroom(classroom.id) ?: throw ApiException.DependencyUnavailable("Classroom persistence is unavailable")).toSummary(true,true)
    }

    suspend fun createInvite(
        actor: UserSummary,
        classroomId: String,
        request: CreateClassInviteRequest,
    ): ClassInvite {
        requireAcademicAdmin(actor)
        val classroom = repository.findClassroom(classroomId)
            ?: throw ApiException.NotFound("Classroom was not found")
        requireScopedAdmin(actor,classroom)
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

        val current=policy().live(actor)
        val group=requireGroup(classroom)
        if(current.institutionId!=group.institutionId) throw ApiException.Forbidden("Classroom enrollment belongs to another institution")
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
        val classroom=repository.findClassroom(classroomId) ?: throw ApiException.NotFound("Classroom was not found")
        requireScopedAdmin(actor,classroom,allowStale=true)
        if (!repository.revokeInvite(inviteId, classroomId, clock.instant())) {
            throw ApiException.NotFound("Active invitation was not found")
        }
    }

    suspend fun nativeChannelsFor(actor: UserSummary): List<ClassroomSummary> = listFor(actor)

    suspend fun requireCanRead(actor:UserSummary,classroomId:String):ClassroomSummary {
        val classroom=repository.findClassroom(classroomId) ?: throw ApiException.NotFound("Classroom was not found")
        val current=policy().live(actor);val group=requireGroup(classroom)
        if(isAcademicAdmin(actor)) requireScopedAdmin(actor,classroom)
        else {
            if(current.institutionId!=group.institutionId) throw ApiException.Forbidden("Classroom belongs to another institution")
            val membership=repository.findMembership(classroomId,actor.id)
            val inherited=groupRepository?.isUserInGroup(actor.id,group.id)==true
            if(!canOperate(actor,classroom) && !(UserRole.STUDENT in current.roles && (membership?.role==ClassroomMemberRole.STUDENT || inherited))) throw ApiException.Forbidden("This classroom is not assigned to your account or group")
        }
        return classroom.toSummary(canOperate(actor,classroom),isAcademicAdmin(actor),repository.findMembership(classroomId,actor.id)?.joinedAt?.epochSecond)
    }
    suspend fun studentIdsForTeacher(actor:UserSummary,classroomId:String):List<String> {
        val current=policy().live(actor)
        val classroom=requireCanPublish(actor,classroomId)
        if(UserRole.TEACHER !in current.roles || classroom.teacherId!=current.id) throw ApiException.Forbidden("Actual assigned teacher is required")
        val group=scopedGroupFor(actor,classroomId)
        val direct=repository.listMembershipsForClass(classroomId).filter { it.role==ClassroomMemberRole.STUDENT }.map { it.userId }
        return (direct+groupRepository?.memberIdsForGroup(group.id).orEmpty()).distinct()
    }
    suspend fun scopedGroupFor(actor:UserSummary,classroomId:String):AcademicGroupRecord {
        requireCanRead(actor,classroomId)
        return requireGroup(repository.findClassroom(classroomId) ?: throw ApiException.NotFound("Classroom was not found"))
    }
    suspend fun requireCanPublish(actor:UserSummary,classroomId:String):ClassroomSummary {
        val classroom=repository.findClassroom(classroomId) ?: throw ApiException.NotFound("Classroom was not found")
        if(!canOperate(actor,classroom)) throw ApiException.Forbidden("Only the current assigned teacher or scoped administration can publish")
        return classroom.toSummary(true,isAcademicAdmin(actor))
    }
    private suspend fun canOperate(actor:UserSummary,classroom:NativeClassroom):Boolean {
        val current=policy().live(actor);val group=requireGroup(classroom)
        if(isAcademicAdmin(actor)) { requireScopedAdmin(actor,classroom);return true }
        return UserRole.TEACHER in current.roles && current.institutionId==group.institutionId && classroom.teacherId==current.id && classroom.teacherRoleGeneration==current.roleGenerations[UserRole.TEACHER]
    }
    private fun validScope(classroom:NativeClassroom,group:AcademicGroupRecord)=classroom.status==ClassroomStatus.ACTIVE && group.active && !group.institutionId.isNullOrBlank() &&
        !group.periodId.isNullOrBlank() && group.scopeRevision>=0 && classroom.groupScopeRevision==group.scopeRevision && group.startsAt!=null && group.endsAt!=null && group.startsAt<=clock.instant() && clock.instant()<group.endsAt
    private suspend fun requireGroup(classroom:NativeClassroom):AcademicGroupRecord {
        val group=classroom.groupId?.let { groupRepository?.find(it) } ?: throw ApiException.Forbidden("Actual classroom group scope is unavailable")
        if(!validScope(classroom,group)) throw ApiException.Forbidden("Current classroom group scope is required")
        return group
    }
    private suspend fun requireScopedAdmin(actor:UserSummary,classroom:NativeClassroom,allowStale:Boolean=false) {
        requireAcademicAdmin(actor)
        val group=if(allowStale) classroom.groupId?.let { groupRepository?.find(it) } ?: throw ApiException.Forbidden("Actual classroom group is required") else requireGroup(classroom)
        policy().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,group.institutionId ?: throw ApiException.Forbidden("Explicit institution is required"))
    }
    private fun policy()=org.companerodeescuela.api.admin.InstitutionScopePolicy(accounts ?: throw ApiException.DependencyUnavailable("Native account authority is unavailable"),clock)
    private fun requireAcademicAdmin(actor:UserSummary) { if(!isAcademicAdmin(actor)) throw ApiException.Forbidden("Academic administration permission is required") }
    private fun isAcademicAdmin(actor:UserSummary)=UserRole.ADMIN in actor.roles || UserRole.SUPER_ADMIN in actor.roles
    private fun boundedId(value:String)=value.isNotBlank() && value==value.trim() && value.length<=128 && value.none(Char::isISOControl)

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
