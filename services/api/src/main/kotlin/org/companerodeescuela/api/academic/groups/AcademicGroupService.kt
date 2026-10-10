package org.companerodeescuela.api.academic.groups

import java.time.Clock
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AcademicGroupMembership
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.AssignAcademicGroupMemberRequest
import org.companerodeescuela.shared.contracts.CreateAcademicGroupRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class AcademicGroupService(
    private val repository: AcademicGroupRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val accounts:org.companerodeescuela.api.auth.PlatformAccountRepository?=null,
    private val mfa:org.companerodeescuela.api.security.MfaService?=null,
    private val institutions:org.companerodeescuela.api.institutions.InstitutionRepository?=null,
) {
    suspend fun changeScope(actor:UserSummary,groupId:String,request:org.companerodeescuela.shared.contracts.AcademicGroupScopeRequest):AcademicGroupSummary {
        val id=normalizeId(groupId)
        val group=repository.find(id) ?: throw ApiException.NotFound("Academic group was not found")
        val policy=org.companerodeescuela.api.admin.InstitutionScopePolicy(accounts ?: throw ApiException.DependencyUnavailable("Native account authority is unavailable"),clock)
        val current=policy.require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,request.institutionId)
        if(group.institutionId==null && UserRole.SUPER_ADMIN !in current.roles) throw ApiException.Forbidden("Only global administration may explicitly migrate an unknown institution")
        if(group.institutionId!=null && group.institutionId!=request.institutionId) throw ApiException.Forbidden("A group's institution cannot be moved with existing academic data")
        (institutions ?: throw ApiException.DependencyUnavailable("Institution directory is unavailable")).findEnabled(request.institutionId)
            ?: throw ApiException.Validation("An enabled institution is required")
        val change=AcademicGroupScopeChange(request.operationId,current.id,request.institutionId,request.periodId,
            java.time.Instant.ofEpochSecond(request.startsAtEpochSeconds.coerceIn(0,4102444800L)),java.time.Instant.ofEpochSecond(request.endsAtEpochSeconds.coerceIn(0,4102444800L)),
            clock.instant(),org.companerodeescuela.shared.contracts.PrivilegedCommandDigests.groupScope(id,request))
        if(request.startsAtEpochSeconds<0 || request.endsAtEpochSeconds>4102444800L || request.startsAtEpochSeconds>=request.endsAtEpochSeconds ||
            request.endsAtEpochSeconds<=clock.instant().epochSecond || !boundedId(request.operationId) || !boundedId(request.periodId) || request.expectedScopeRevision<0)
            throw ApiException.Validation("A bounded operation, period, revision and valid dates are required")
        (mfa ?: throw ApiException.DependencyUnavailable("MFA is unavailable")).requireStepUp(current,request.proof,
            org.companerodeescuela.shared.contracts.StepUpAction.MANAGE_ACADEMICS,id,request.operationId,change.payloadDigest)
        for(member in repository.memberIdsForGroup(id)) {
            val account=accounts.findById(member)
            if(account==null || account.institutionId!=request.institutionId) throw ApiException.Conflict("Group members require explicit matching identity linkage before migration")
        }
        return (repository.changeScope(id,request.expectedScopeRevision,change) ?: throw ApiException.Conflict("Group scope changed or migration was already used with another payload")).toSummary()
    }
    private fun boundedId(value:String)=value.isNotBlank() && value==value.trim() && value.length<=128 && value.none(Char::isISOControl)

    suspend fun listFor(actor:UserSummary):List<AcademicGroupSummary> {
        val current=policy().live(actor)
        val records=if(isAdmin(actor)) {
            policy().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,current.institutionId ?: "global")
            repository.list().filter { UserRole.SUPER_ADMIN in current.roles || it.institutionId==current.institutionId }
        } else repository.listGroupsForUser(actor.id).filter { it.institutionId!=null && it.institutionId==current.institutionId }
        return records.filter { it.active && it.scopeRevision>=0 }.map { it.toSummary() }
    }
    suspend fun create(actor:UserSummary,request:CreateAcademicGroupRequest):AcademicGroupSummary {
        requireAdmin(actor)
        val id=normalizeId(request.id);val name=request.name.trim()
        if(name.length !in 2..120 || name.any(Char::isISOControl)) throw ApiException.Validation("A bounded group name is required")
        val institution=request.institutionId ?: throw ApiException.Validation("Explicit institution is required")
        val current=policy().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,institution)
        (institutions ?: throw ApiException.DependencyUnavailable("Institution directory is unavailable")).findEnabled(institution) ?: throw ApiException.Validation("An enabled institution is required")
        val operation=request.operationId ?: throw ApiException.Forbidden("Scoped MFA operation is required")
        val proof=request.proof ?: throw ApiException.Forbidden("Scoped MFA proof is required")
        val start=request.startsAtEpochSeconds ?: throw ApiException.Validation("Start date is required")
        val end=request.endsAtEpochSeconds ?: throw ApiException.Validation("End date is required")
        val period=request.periodId ?: throw ApiException.Validation("Period is required")
        if(!boundedId(operation) || !boundedId(period) || start<0 || start>=end || end>4102444800L || end<=clock.instant().epochSecond) throw ApiException.Validation("Valid period and dates are required")
        val digest=org.companerodeescuela.shared.contracts.PrivilegedCommandDigests.groupCreation(id,request)
        (mfa ?: throw ApiException.DependencyUnavailable("MFA is unavailable")).requireStepUp(current,proof,org.companerodeescuela.shared.contracts.StepUpAction.MANAGE_ACADEMICS,id,operation,digest)
        val change=AcademicGroupScopeChange(operation,current.id,institution,period,java.time.Instant.ofEpochSecond(start),java.time.Instant.ofEpochSecond(end),clock.instant(),digest)
        val initial=AcademicGroupRecord(id,name,true,clock.instant()).applyScopeChange(0,change) ?: throw ApiException.Validation("Invalid group scope")
        repository.create(initial)
        return (repository.find(id) ?: throw ApiException.DependencyUnavailable("Group persistence is unavailable")).toSummary()
    }
    suspend fun assignMember(actor:UserSummary,groupId:String,request:AssignAcademicGroupMemberRequest):AcademicGroupMembership {
        requireAdmin(actor)
        val group=repository.find(normalizeId(groupId)) ?: throw ApiException.NotFound("Academic group was not found")
        val institution=group.institutionId ?: throw ApiException.Forbidden("Explicit group institution is required")
        val current=policy().require(actor,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,institution)
        val operation=request.operationId ?: throw ApiException.Forbidden("Scoped MFA operation is required")
        val proof=request.proof ?: throw ApiException.Forbidden("Scoped MFA proof is required")
        if(!boundedId(operation) || !boundedId(request.userId)) throw ApiException.Validation("Bounded operation and student ID are required")
        if(!group.active || group.scopeRevision<0 || group.scopeRevision!=request.expectedScopeRevision || group.startsAt==null || group.endsAt==null || group.endsAt<=clock.instant()) throw ApiException.Conflict("A current active group scope is required")
        val digest=org.companerodeescuela.shared.contracts.PrivilegedCommandDigests.groupMembership(group.id,request)
        (mfa ?: throw ApiException.DependencyUnavailable("MFA is unavailable")).requireStepUp(current,proof,org.companerodeescuela.shared.contracts.StepUpAction.MANAGE_ACADEMICS,group.id,operation,digest)
        val target=accounts?.findById(request.userId) ?: throw ApiException.Validation("Verified native student is required")
        if(!target.permitsSession || target.accountStatus!=org.companerodeescuela.shared.contracts.AccountStatus.ACTIVE || target.emailVerifiedAt==null || target.emailVerifiedAt>clock.instant() || UserRole.STUDENT !in target.roles || target.institutionId!=institution) throw ApiException.Validation("A current verified student in this institution is required")
        val membership=repository.assign(AcademicGroupMembershipRecord(group.id,target.id,clock.instant(),current.id,operation,digest,group.scopeRevision))
        return AcademicGroupMembership(group.id,group.name,membership.userId,membership.joinedAt.epochSecond)
    }
    private fun policy()=org.companerodeescuela.api.admin.InstitutionScopePolicy(accounts ?: throw ApiException.DependencyUnavailable("Native identity authority is unavailable"),clock)

    private fun normalizeId(raw: String): String =
        raw.trim().uppercase(java.util.Locale.ROOT).replace(Regex("\\s+"), "-")
            .takeIf { boundedId(it) }
            ?: throw ApiException.Validation("A bounded group id is required")

    private fun requireAdmin(actor: UserSummary) {
        if (!isAdmin(actor)) {
            throw ApiException.Forbidden("Academic administration permission is required")
        }
    }

    private fun isAdmin(actor: UserSummary): Boolean =
        UserRole.ADMIN in actor.roles || UserRole.SUPER_ADMIN in actor.roles

    private fun AcademicGroupRecord.toSummary(): AcademicGroupSummary =
        AcademicGroupSummary(id = id, name = name, active = active,institutionId=institutionId,periodId=periodId,startsAtEpochSeconds=startsAt?.epochSecond,endsAtEpochSeconds=endsAt?.epochSecond,scopeRevision=scopeRevision)
}
