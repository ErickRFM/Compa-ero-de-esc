package org.companerodeescuela.api.tutoring

import java.time.Clock
import java.util.UUID
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.PlatformAccount
import org.companerodeescuela.api.admin.InstitutionScopePolicy
import org.companerodeescuela.api.academic.groups.AcademicGroupRecord
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.TutorApprovalScope
import org.companerodeescuela.shared.contracts.PrivilegedCommandDigests
import org.companerodeescuela.shared.contracts.StepUpAction
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.CreateTutorAssignmentRequest
import org.companerodeescuela.shared.contracts.TutorAssignmentSummary
import org.companerodeescuela.shared.contracts.TutorScopeSummary
import org.companerodeescuela.shared.contracts.TutorStudentSummary
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

/**
 * Tutors are appointed by administration, not by teachers or self-registration.
 * Account validation is fail-closed until an identity repository is provided.
 * Institutional-only identities require an explicit adapter before appointment.
 */
class TutorAssignmentService(
    private val repository: TutorAssignmentRepository,
    private val groupRepository: AcademicGroupRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val accounts: PlatformAccountRepository? = null,
    private val mfa: org.companerodeescuela.api.security.MfaService? = null,
) {
    suspend fun listFor(actor: UserSummary): List<TutorAssignmentSummary> {
        val records = if (isAdmin(actor)) {
            val current=requireAdmin(actor)
            repository.listAll().filter { record ->
                UserRole.SUPER_ADMIN in current.roles || (record.institutionId!=null && record.institutionId==current.institutionId)
            }
        } else {
            val current=requireTutor(actor)
            repository.listForTutor(actor.id).filter { record ->
                groupRepository.find(record.academicGroupId)?.let { validAssignment(current,it,record) }==true
            }
        }
        return records.mapNotNull { record ->
            val group = groupRepository.find(record.academicGroupId) ?: return@mapNotNull null
            record.toSummary(group.name)
        }
    }

    suspend fun create(actor:UserSummary,request:CreateTutorAssignmentRequest):TutorAssignmentSummary {
        val currentActor=requireAdmin(actor)
        val tutorId=identifier(request.tutorUserId)
        val groupId=identifier(request.academicGroupId)
        val group=groupRepository.find(groupId) ?: throw ApiException.NotFound("Academic group was not found")
        val institution=group.institutionId ?: throw ApiException.Validation("Explicit group institution is required")
        policy().require(actor,AdminCapability.MANAGE_ACADEMICS,institution)
        if(request.institutionId!=institution) throw ApiException.Validation("Assignment institution must match the actual group")
        val operation=identifier(request.operationId ?: throw ApiException.Forbidden("A scoped MFA operation is required"))
        val evidence=request.proof ?: throw ApiException.Forbidden("A scoped MFA proof is required")
        val digest=PrivilegedCommandDigests.tutorAssignment(groupId,request)
        (mfa ?: throw ApiException.DependencyUnavailable("MFA is not configured")).requireStepUp(currentActor,evidence,StepUpAction.MANAGE_ACADEMICS,groupId,operation,digest)
        val target=(accounts ?: throw ApiException.DependencyUnavailable("Tutor authority is unavailable")).findById(tutorId) ?: throw ApiException.NotFound("Tutor account was not found")
        if(!target.permitsSession || target.accountStatus!=AccountStatus.ACTIVE || target.emailVerifiedAt==null || target.emailVerifiedAt>clock.instant() || UserRole.TUTOR !in target.roles || target.institutionId!=institution) throw ApiException.Validation("A currently verified active tutor in this institution is required")
        val scope=TutorApprovalScope(groupId,request.periodId ?: throw ApiException.Validation("Period is required"),
            request.startsAtEpochSeconds ?: throw ApiException.Validation("Start date is required"),request.endsAtEpochSeconds ?: throw ApiException.Validation("End date is required"),request.groupScopeRevision)
        val id="tutor-"+UUID.nameUUIDFromBytes((currentActor.id+"\u0000"+operation).toByteArray(Charsets.UTF_8)).toString()
        val generation=target.roleGenerations[UserRole.TUTOR] ?: throw ApiException.Validation("Current tutor generation is required")
        validateApprovalScope(currentActor,tutorId,institution,scope,id,generation,AdminCapability.MANAGE_ACADEMICS)
        val record=repository.create(TutorAssignmentRecord(id,tutorId,groupId,true,currentActor.id,clock.instant(),
            institutionId=institution,periodId=scope.periodId,startsAt=java.time.Instant.ofEpochSecond(scope.startsAtEpochSeconds),endsAt=java.time.Instant.ofEpochSecond(scope.endsAtEpochSeconds),tutorRoleGeneration=generation,groupScopeRevision=scope.groupScopeRevision))
        return record.toSummary(group.name)
    }
    suspend fun revoke(actor:UserSummary,assignmentId:String,request:org.companerodeescuela.shared.contracts.RevokeTutorAssignmentRequest?=null):TutorAssignmentSummary {
        val currentActor=requireAdmin(actor);val id=identifier(assignmentId)
        val current=repository.findById(id) ?: throw ApiException.NotFound("Tutor assignment was not found")
        val group=groupRepository.find(current.academicGroupId) ?: throw ApiException.Forbidden("Assignment group scope is unavailable")
        val institution=group.institutionId ?: throw ApiException.Forbidden("Explicit group institution is required")
        if(current.institutionId!=institution) throw ApiException.Forbidden("Assignment institution differs from group")
        policy().require(actor,AdminCapability.MANAGE_ACADEMICS,institution)
        val command=request ?: throw ApiException.Forbidden("A scoped MFA revocation is required")
        identifier(command.operationId)
        val digest=PrivilegedCommandDigests.tutorRevocation(id,command)
        (mfa ?: throw ApiException.DependencyUnavailable("MFA is not configured")).requireStepUp(currentActor,command.proof,StepUpAction.MANAGE_ACADEMICS,id,command.operationId,digest)
        if(!current.active && (current.revokedBy!=currentActor.id || current.revocationOperationId!=command.operationId || current.revocationDigest!=digest)) throw ApiException.Conflict("Assignment was revoked by another operation")
        val updated=repository.revoke(id,currentActor.id,clock.instant(),command.operationId,digest) ?: throw ApiException.NotFound("Tutor assignment was not found")
        if(updated.revokedBy!=currentActor.id || updated.revocationOperationId!=command.operationId || updated.revocationDigest!=digest) throw ApiException.Conflict("Assignment revocation changed concurrently")
        return updated.toSummary(group.name)
    }

    suspend fun studentsForGroup(actor: UserSummary, rawGroupId: String): List<TutorStudentSummary> {
        val groupId = rawGroupId.trim().uppercase()
        if (groupId.isBlank()) throw ApiException.Validation("Group ID is required")
        requireCanAccessGroup(actor, groupId)
        return groupRepository.memberIdsForGroup(groupId).mapNotNull { memberId ->
            val platformAccount = accounts?.findById(memberId)
            if (platformAccount != null &&
                (!platformAccount.active || UserRole.STUDENT !in platformAccount.roles)) {
                return@mapNotNull null
            }
            TutorStudentSummary(
                userId = memberId,
                academicGroupId = groupId,
                displayName = platformAccount?.displayName,
                verifiedPlatformStudent = platformAccount != null,
            )
        }
    }

    suspend fun scopeFor(actor: UserSummary): TutorScopeSummary {
        val current=requireTutor(actor)
        val groups = repository.listForTutor(actor.id)
            .mapNotNull { record -> groupRepository.find(record.academicGroupId)?.takeIf { validAssignment(current,it,record) } }
            .distinctBy { it.id }
            .map { AcademicGroupSummary(id = it.id, name = it.name, active = true) }
        return TutorScopeSummary(tutorUserId = actor.id, groups = groups)
    }

    suspend fun requireCanAccessGroup(actor: UserSummary, academicGroupId: String) {
        if (isAdmin(actor)) {
            val group=groupRepository.find(academicGroupId.trim().uppercase()) ?: throw ApiException.Forbidden("Academic group scope is unavailable")
            val institution=group.institutionId ?: throw ApiException.Forbidden("Group institution must be explicitly assigned")
            policy().require(actor,AdminCapability.MANAGE_ACADEMICS,institution)
            return
        }
        requireTutorAssignmentForGroup(actor, academicGroupId)
    }

    /** Private cases require an actual tutor assignment even for administrative accounts. */
    suspend fun requireTutorAssignmentForGroup(actor: UserSummary, academicGroupId: String) {
        val current=requireTutor(actor)
        val group = groupRepository.find(academicGroupId.trim().uppercase())
        val assignment = group?.takeIf { it.active }?.let {
            repository.findActive(actor.id, it.id)
        }
        if (group==null || assignment==null || !validAssignment(current,group,assignment)) {
            throw ApiException.Forbidden("Tutor is not assigned to this active academic group")
        }
    }

    private fun TutorAssignmentRecord.toSummary(groupName: String) = TutorAssignmentSummary(
        id = id,
        tutorUserId = tutorUserId,
        academicGroupId = academicGroupId,
        academicGroupName = groupName,
        active = active,
        assignedBy = assignedBy,
        assignedAtEpochSeconds = assignedAt.epochSecond,
        revokedAtEpochSeconds = revokedAt?.epochSecond,
        revokedBy = revokedBy,
        institutionId = institutionId, periodId = periodId, startsAtEpochSeconds = startsAt?.epochSecond,
        endsAtEpochSeconds = endsAt?.epochSecond, tutorRoleGeneration = tutorRoleGeneration,groupScopeRevision=groupScopeRevision,
    )

    /** Returns whether group administration may inspect all pending representative invitations. */
    internal suspend fun requireRepresentativeRead(actor:UserSummary,groupId:String):Boolean {
        if(isAdmin(actor)) { requireCanAccessGroup(actor,groupId);return true }
        if(UserRole.TUTOR in actor.roles) { requireTutorAssignmentForGroup(actor,groupId);return true }
        if(UserRole.STUDENT !in actor.roles) throw ApiException.Forbidden("Group membership is required")
        val current=policy().live(actor)
        val group=groupRepository.find(groupId) ?: throw ApiException.Forbidden("Group scope is unavailable")
        if(!group.active || current.institutionId!=group.institutionId || !groupRepository.isUserInGroup(actor.id,groupId)) throw ApiException.Forbidden("Student does not belong to this group")
        return false
    }
    internal suspend fun requireRepresentativeCandidate(groupId:String,studentId:String) {
        val group=groupRepository.find(groupId) ?: throw ApiException.Forbidden("Group scope is unavailable")
        val account=accounts?.findById(studentId) ?: throw ApiException.Validation("A verified native student is required")
        if(!group.active || !account.permitsSession || account.accountStatus!=AccountStatus.ACTIVE || account.emailVerifiedAt==null || account.emailVerifiedAt>clock.instant() ||
            UserRole.STUDENT !in account.roles || account.institutionId!=group.institutionId || !groupRepository.isUserInGroup(studentId,groupId)) throw ApiException.Validation("Representative candidate must belong to this active group")
    }

    /** Internal approval continuation: the administrative service has already consumed exact command MFA. */
    internal suspend fun validateApprovalScope(actor:PlatformAccount,targetId:String,institutionId:String,scope:TutorApprovalScope,assignmentId:String,generation:Long?,capability:AdminCapability=AdminCapability.APPROVE_STAFF) {
        policy().require(actor.toUserSummary(),capability,institutionId)
        val group=groupRepository.find(scope.academicGroupId) ?: throw ApiException.Validation("Tutor group must exist")
        val groupStart=group.startsAt ?: throw ApiException.Validation("Explicit group validity is required")
        val groupEnd=group.endsAt ?: throw ApiException.Validation("Explicit group validity is required")
        if(!group.active || group.institutionId!=institutionId || group.periodId!=scope.periodId || group.scopeRevision<0 || group.scopeRevision!=scope.groupScopeRevision ||
            scope.startsAtEpochSeconds<0 || scope.endsAtEpochSeconds>4102444800L || scope.startsAtEpochSeconds>=scope.endsAtEpochSeconds ||
            scope.startsAtEpochSeconds<groupStart.epochSecond || scope.endsAtEpochSeconds>groupEnd.epochSecond || scope.endsAtEpochSeconds<=clock.instant().epochSecond)
            throw ApiException.Validation("Tutor assignment must match institution, period and bounded valid dates")
        val existing=repository.findById(assignmentId) ?: repository.findActive(targetId,group.id)
        if(existing!=null && (existing.id!=assignmentId || !existing.active || existing.revokedAt!=null || existing.institutionId!=institutionId ||
            existing.groupScopeRevision!=scope.groupScopeRevision || existing.periodId!=scope.periodId || existing.startsAt?.epochSecond!=scope.startsAtEpochSeconds || existing.endsAt?.epochSecond!=scope.endsAtEpochSeconds ||
            (generation!=null && existing.tutorRoleGeneration!=generation))) throw ApiException.Conflict("Existing assignment must be explicitly revoked before changing scope")
    }
    internal suspend fun createApproved(actor:PlatformAccount,target:PlatformAccount,institutionId:String,scope:TutorApprovalScope,assignmentId:String,decidedAt:java.time.Instant):TutorAssignmentSummary {
        val current=policy().live(target.toUserSummary())
        if(UserRole.TUTOR !in current.roles || current.institutionId!=institutionId) throw ApiException.Conflict("Approved tutor authority is no longer current")
        val generation=current.roleGenerations[UserRole.TUTOR] ?: throw ApiException.Conflict("Current tutor role generation is required")
        validateApprovalScope(actor,current.id,institutionId,scope,assignmentId,generation)
        val record=repository.create(TutorAssignmentRecord(assignmentId,current.id,scope.academicGroupId,true,actor.id,decidedAt,
            institutionId=institutionId,periodId=scope.periodId,startsAt=java.time.Instant.ofEpochSecond(scope.startsAtEpochSeconds),
            endsAt=java.time.Instant.ofEpochSecond(scope.endsAtEpochSeconds),tutorRoleGeneration=generation,groupScopeRevision=scope.groupScopeRevision))
        return record.toSummary(groupRepository.find(record.academicGroupId)?.name ?: record.academicGroupId)
    }

    private suspend fun requireTutor(actor:UserSummary):PlatformAccount {
        if(!actor.active || actor.accountStatus!=AccountStatus.ACTIVE) throw ApiException.Unauthorized()
        if(UserRole.TUTOR !in actor.roles) throw ApiException.Forbidden("Tutor role is required")
        return policy().live(actor)
    }
    private suspend fun requireAdmin(actor:UserSummary):PlatformAccount {
        if(!isAdmin(actor)) throw ApiException.Forbidden("Academic administration permission is required")
        return policy().require(actor,AdminCapability.MANAGE_ACADEMICS,actor.institutionId ?: "global")
    }
    private fun policy()=InstitutionScopePolicy(accounts ?: throw ApiException.DependencyUnavailable("Native identity authority is not configured"),clock)
    private fun validAssignment(current:PlatformAccount,group:AcademicGroupRecord,assignment:TutorAssignmentRecord):Boolean {
        val now=clock.instant()
        val groupStart=group.startsAt ?: return false;val groupEnd=group.endsAt ?: return false
        val start=assignment.startsAt ?: return false;val end=assignment.endsAt ?: return false
        return group.active && !group.institutionId.isNullOrBlank() && !group.periodId.isNullOrBlank() &&
            current.institutionId==group.institutionId && assignment.institutionId==group.institutionId &&
            group.scopeRevision>=0 && assignment.groupScopeRevision==group.scopeRevision && assignment.periodId==group.periodId && assignment.tutorUserId==current.id && assignment.academicGroupId==group.id &&
            assignment.active && assignment.revokedAt==null && assignment.tutorRoleGeneration>=0 &&
            current.roleGenerations[UserRole.TUTOR]==assignment.tutorRoleGeneration && groupStart<=now && now<groupEnd &&
            groupStart<=start && start<end && end<=groupEnd && start<=now && now<end
    }
    private fun identifier(value:String):String {
        if(value.isBlank() || value!=value.trim() || value.length>128 || value.any(Char::isISOControl)) throw ApiException.Validation("A bounded operation or resource ID is required")
        return value
    }
    private fun isAdmin(actor:UserSummary)=UserRole.ADMIN in actor.roles || UserRole.SUPER_ADMIN in actor.roles
}
