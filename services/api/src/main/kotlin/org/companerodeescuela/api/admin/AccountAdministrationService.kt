package org.companerodeescuela.api.admin

import java.time.Clock
import java.time.Instant
import java.security.MessageDigest
import org.companerodeescuela.api.auth.*
import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentService
import org.companerodeescuela.api.security.MfaService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*

/** Approval updates one account atomically; deterministic assignment continuation never implies a multi-document transaction. */
class AccountAdministrationService(private val accounts:PlatformAccountRepository,private val institutions:InstitutionRepository,
    private val groups:AcademicGroupRepository,private val tutoring:TutorAssignmentService,private val mfa:MfaService,
    private val clock:Clock=Clock.systemUTC()) {
    private val policy=InstitutionScopePolicy(accounts,clock)
    suspend fun overview(actor:PlatformAccount):AdministrationOverview {
        val capability=if(AdminCapability.APPROVE_STAFF in actor.adminCapabilities) AdminCapability.APPROVE_STAFF else AdminCapability.MANAGE_USERS
        val current=policy.require(actor.toUserSummary(),capability,actor.institutionId ?: "global")
        val records=accounts.listAccounts().filter { UserRole.SUPER_ADMIN in current.roles || effectiveInstitution(it)==current.institutionId }
        val users=records.map(::summary).sortedBy { it.user.id }
        return AdministrationOverview(users,AdministrationMetrics(users.size,records.sumOf { it.staffApplications().count { application -> application.status==StaffApplicationStatus.PENDING } },
            records.count { it.permitsSession && it.accountStatus==AccountStatus.ACTIVE && UserRole.TEACHER in it.roles },
            records.count { it.permitsSession && it.accountStatus==AccountStatus.ACTIVE && UserRole.TUTOR in it.roles },records.count { it.accountStatus==AccountStatus.SUSPENDED }))
    }
    suspend fun requestTutor(actor:PlatformAccount,request:TutorApplicationRequest):StaffApplication {
        val current=policy.live(actor.toUserSummary())
        if(UserRole.TEACHER !in current.roles || UserRole.TUTOR in current.roles || current.roles.any { it==UserRole.ADMIN || it==UserRole.SUPER_ADMIN }) throw ApiException.Forbidden("An approved teacher may request supplementary tutor access")
        identifier(request.operationId);identifier(request.institutionId)
        if(request.expectedAuthRevision!=current.authRevision || current.institutionId!=request.institutionId) throw ApiException.Conflict("Teacher institution or revision is not current")
        if(institutions.findEnabled(request.institutionId)==null) throw ApiException.Validation("Requested institution is unavailable")
        val reference=request.identityReference.trim();val preference=request.preferredGroup?.trim()
        if(reference.length !in 1..160 || reference.any(Char::isISOControl) || (preference!=null && (preference.length !in 1..120 || preference.any(Char::isISOControl)))) throw ApiException.Validation("Tutor identity reference or group preference is invalid")
        val id="tutor-request-"+request.operationId
        identifier(id)
        val previous=current.roleApplications.find { it.id==id }
        val application=StaffApplication(id,StaffRole.TUTOR,request.institutionId,reference,preference,previous?.requestedAtEpochSeconds ?: clock.instant().epochSecond)
        val saved=accounts.submitStaffApplication(current.id,current.authRevision,application) ?: throw ApiException.Conflict("Tutor application is already pending or authority changed")
        return saved.roleApplications.first { it.id==id }
    }
    suspend fun approveStaff(actor:PlatformAccount,targetId:String,request:ApproveStaffRequest):AdminUserSummary {
        identifier(targetId);identifier(request.operationId);identifier(request.applicationId);identifier(request.institutionId)
        val currentActor=policy.require(actor.toUserSummary(),AdminCapability.APPROVE_STAFF,request.institutionId)
        val target=accounts.findById(targetId) ?: throw ApiException.NotFound("Staff applicant was not found")
        if(target.id==currentActor.id || target.roles.any { it==UserRole.ADMIN || it==UserRole.SUPER_ADMIN }) throw ApiException.Forbidden("Privileged identities cannot obtain authority through staff approval")
        if(effectiveInstitution(target)!=request.institutionId) throw ApiException.Forbidden("Applicant belongs to another institution")
        val digest=PrivilegedCommandDigests.staffApproval(targetId,request)
        mfa.requireStepUp(currentActor,request.proof,StepUpAction.APPROVE_STAFF,targetId,request.operationId,digest)
        if(institutions.findEnabled(request.institutionId)==null) throw ApiException.Validation("Approval institution must be enabled")
        if(!target.permitsSession || target.accountStatus !in setOf(AccountStatus.PENDING_APPROVAL,AccountStatus.ACTIVE) || target.emailVerifiedAt==null || target.emailVerifiedAt>clock.instant()) throw ApiException.Conflict("Applicant is not currently verified and approvable")
        val application=target.staffApplications().find { it.id==request.applicationId } ?: throw ApiException.Conflict("Current staff application is required")
        if(application.requestedInstitutionId!=request.institutionId) throw ApiException.Forbidden("Application institution differs from approval")
        val role=if(application.role==StaffRole.TUTOR) UserRole.TUTOR else UserRole.TEACHER
        val retry=application.status==StaffApplicationStatus.APPROVED && application.decisionOperationId==request.operationId &&
            application.decisionDigest==digest && application.decidedBy==currentActor.id && target.roleGenerations[role]==application.grantedRoleGeneration && role in target.roles &&
            target.identityAudit.any { it.operationId==request.operationId && it.fromRevision==request.expectedAuthRevision && it.scopeDigest==digest && it.approvedApplicationId==application.id }
        if(!retry && (application.status!=StaffApplicationStatus.PENDING || target.authRevision!=request.expectedAuthRevision || role in target.roles)) throw ApiException.Conflict("Application was decided or authority changed")
        if(application.role==StaffRole.TUTOR) {
            if(request.tutorScopes.isEmpty() || request.tutorScopes.size>20 || request.tutorScopes.map { it.academicGroupId }.distinct().size!=request.tutorScopes.size) throw ApiException.Validation("One to twenty distinct approved groups are required")
            for(scope in request.tutorScopes) {
                identifier(scope.academicGroupId);identifier(scope.periodId)
                tutoring.validateApprovalScope(currentActor,targetId,request.institutionId,scope,assignmentId(targetId,application.id,request.operationId,scope.academicGroupId),if(retry) application.grantedRoleGeneration else null)
            }
        } else if(request.tutorScopes.isNotEmpty()) throw ApiException.Validation("Teacher approval cannot grant tutor assignments")
        val approved=if(retry) target else accounts.changeIdentity(targetId,request.expectedAuthRevision,
            AccountIdentityChange(request.operationId,currentActor.id,AccountStatus.ACTIVE,target.roles+role,request.institutionId,clock.instant(),digest,application.id))
            ?: throw ApiException.Conflict("Staff approval changed concurrently")
        val decision=approved.roleApplications.find { it.id==application.id } ?: throw ApiException.Conflict("Committed application decision is unavailable")
        if(approved.accountStatus!=AccountStatus.ACTIVE || !approved.permitsSession || role !in approved.roles || approved.roleGenerations[role]!=decision.grantedRoleGeneration) throw ApiException.Conflict("Approved grant is no longer current")
        for(scope in request.tutorScopes) tutoring.createApproved(currentActor,approved,request.institutionId,scope,assignmentId(targetId,application.id,request.operationId,scope.academicGroupId),Instant.ofEpochSecond(requireNotNull(decision.decidedAtEpochSeconds)))
        return summary(approved)
    }
    suspend fun lifecycle(actor:PlatformAccount,targetId:String,request:AccountLifecycleRequest):AdminUserSummary {
        identifier(targetId);identifier(request.operationId)
        val currentActor=policy.live(actor.toUserSummary())
        val target=accounts.findById(targetId) ?: throw ApiException.NotFound("Account was not found")
        val institution=effectiveInstitution(target) ?: throw ApiException.Forbidden("Account institution has not been assigned")
        policy.require(currentActor.toUserSummary(),AdminCapability.MANAGE_USERS,institution)
        if(target.id==currentActor.id || (UserRole.SUPER_ADMIN !in currentActor.roles && target.roles.any { it==UserRole.ADMIN || it==UserRole.SUPER_ADMIN })) throw ApiException.Forbidden("Privileged account changes require a distinct authorized owner")
        val digest=PrivilegedCommandDigests.accountLifecycle(targetId,request)
        mfa.requireStepUp(currentActor,request.proof,StepUpAction.ACCOUNT_LIFECYCLE,targetId,request.operationId,digest)
        val exact=target.identityAudit.any { it.operationId==request.operationId && it.actorId==currentActor.id && it.fromRevision==request.expectedAuthRevision && it.scopeDigest==digest && it.toStatus==request.status }
        if(exact) return summary(target)
        if(request.expectedAuthRevision!=target.authRevision || target.accountStatus==AccountStatus.REVOKED) throw ApiException.Conflict("Account authority changed")
        if(request.status !in setOf(AccountStatus.SUSPENDED,AccountStatus.REVOKED)) {
            val prior=target.identityAudit.lastOrNull()?.fromStatus
            if(target.accountStatus!=AccountStatus.SUSPENDED || request.status!=prior ||
                (request.status==AccountStatus.ACTIVE && (target.roles.isEmpty() || target.emailVerifiedAt==null))) throw ApiException.Forbidden("Restore must retain the original lifecycle and grants")
        }
        val changed=accounts.changeIdentity(targetId,target.authRevision,AccountIdentityChange(request.operationId,currentActor.id,request.status,target.roles,target.institutionId,clock.instant(),digest))
            ?: throw ApiException.Conflict("Account changed concurrently")
        return summary(changed)
    }
    private fun summary(account:PlatformAccount)=AdminUserSummary(account.toUserSummary(),account.staffApplications(),account.adminCapabilities)
    private fun effectiveInstitution(account:PlatformAccount)=account.institutionId ?: account.registrationIntent?.requestedInstitutionId
    private fun identifier(value:String) {
        if(value.isBlank() || value!=value.trim() || value.length>128 || value.any(Char::isISOControl)) throw ApiException.Validation("A bounded operation and resource identifier is required")
    }
    private fun assignmentId(target:String,application:String,operation:String,group:String):String = "tutor-"+MessageDigest.getInstance("SHA-256").digest(listOf(target,application,operation,group).joinToString("\u0000").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
}
