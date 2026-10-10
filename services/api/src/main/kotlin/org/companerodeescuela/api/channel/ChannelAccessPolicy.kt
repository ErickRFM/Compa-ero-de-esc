package org.companerodeescuela.api.channel

import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.academic.dto.ExternalCourse
import org.companerodeescuela.api.representatives.GroupRepresentativeService
import org.companerodeescuela.shared.contracts.ChannelType
import org.companerodeescuela.shared.contracts.ClassChannelSummary
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class ChannelAccessPolicy(
    private val academicProvider: AcademicProvider,
    private val classroomService: ClassroomService? = null,
    private val representativeService: GroupRepresentativeService? = null,
    private val accounts:org.companerodeescuela.api.auth.PlatformAccountRepository?=null,
    private val groupRepository:org.companerodeescuela.api.academic.groups.AcademicGroupRepository?=null,
) {
    suspend fun channelsFor(userId:String,roles:Set<UserRole>,actor:UserSummary?=null,identitySource:org.companerodeescuela.api.auth.SessionIdentitySource=org.companerodeescuela.api.auth.SessionIdentitySource.INSTITUTIONAL):List<ClassChannelSummary> {
        if(identitySource==org.companerodeescuela.api.auth.SessionIdentitySource.LEGACY) throw ApiException.Unauthorized()
        if(identitySource==org.companerodeescuela.api.auth.SessionIdentitySource.INSTITUTIONAL) {
            // An institutional administrator needs an explicit institution/resource adapter; a role is not global provider authority.
            if(roles.any { it==UserRole.ADMIN || it==UserRole.SUPER_ADMIN || it==UserRole.COORDINATOR }) return emptyList()
            val courses=accessibleCourses(userId,roles)
            return courses.distinctBy(ExternalCourse::externalId).sortedWith(compareBy({it.subject.name},{it.groupName})).map { course ->
                ClassChannelSummary(course.externalId,course.externalId,course.subject.externalCode,course.subject.name,course.groupName,course.term,
                    course.teacher.externalId,course.teacher.fullName,UserRole.TEACHER in roles && course.teacher.externalId==userId,ChannelType.CLASS)
            }
        }
        val claimed=actor ?: throw ApiException.Unauthorized()
        if(claimed.id!=userId || claimed.roles!=roles) throw ApiException.Unauthorized()
        val policy=org.companerodeescuela.api.admin.InstitutionScopePolicy(accounts ?: throw ApiException.DependencyUnavailable("Native identity authority is unavailable"))
        val current=policy.live(claimed)
        val admin=UserRole.ADMIN in roles || UserRole.SUPER_ADMIN in roles
        if(admin) policy.require(claimed,org.companerodeescuela.shared.contracts.AdminCapability.MANAGE_ACADEMICS,current.institutionId ?: "global")
        val native=classroomService?.nativeChannelsFor(current.toUserSummary()).orEmpty().map { classroom ->
            ClassChannelSummary(classroom.id,classroom.id,"LOCAL",classroom.name,classroom.groupName.orEmpty(),"Compañero",classroom.teacherId,classroom.teacherDisplayName,classroom.canManage,ChannelType.CLASS)
        }
        val groupInstitutions=groupRepository?.list().orEmpty().filter { it.active && it.scopeRevision>=0 && it.institutionId!=null && it.periodId!=null && it.startsAt!=null && it.endsAt!=null && it.startsAt<=java.time.Instant.now() && java.time.Instant.now()<it.endsAt }
            .mapNotNull { it.institutionId }.toSet()
        val institutions=when {
            admin -> if(UserRole.SUPER_ADMIN in roles) groupInstitutions else groupInstitutions.filter { it==current.institutionId }.toSet()
            UserRole.TEACHER in roles && native.any { it.canPublish } -> setOfNotNull(current.institutionId)
            UserRole.STUDENT in roles -> representativeService?.activeAssignmentsForStudent(userId).orEmpty().mapNotNull { it.institutionId }.filter { it==current.institutionId }.toSet()
            else -> emptySet()
        }
        val representatives=institutions.sorted().map { institution ->
            val id="channel-representatives:"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(institution.toByteArray(Charsets.UTF_8))
            ClassChannelSummary(id,id,"INST","Representantes de Grupo",institution,"Compañero","",institution,admin,ChannelType.REPRESENTATIVES)
        }
        return (native+representatives).distinctBy { it.id }
    }
    suspend fun requireCanRead(userId:String,roles:Set<UserRole>,channelId:String,actor:UserSummary?=null,identitySource:org.companerodeescuela.api.auth.SessionIdentitySource=org.companerodeescuela.api.auth.SessionIdentitySource.INSTITUTIONAL):ClassChannelSummary =
        channelsFor(userId,roles,actor,identitySource).firstOrNull { it.id==channelId } ?: throw ApiException.Forbidden("This class channel is not assigned to your account")
    suspend fun requireCanPublish(userId:String,roles:Set<UserRole>,channelId:String,actor:UserSummary?=null,identitySource:org.companerodeescuela.api.auth.SessionIdentitySource=org.companerodeescuela.api.auth.SessionIdentitySource.INSTITUTIONAL):ClassChannelSummary {
        val channel=requireCanRead(userId,roles,channelId,actor,identitySource)
        if(!channel.canPublish) throw ApiException.Forbidden("Only current assigned teachers or scoped administration can publish")
        return channel
    }
    private suspend fun accessibleCourses(userId:String,roles:Set<UserRole>):List<ExternalCourse> = translateIntegrationFailure {
        val taught=if(UserRole.TEACHER in roles) academicProvider.listCourses().filter { it.teacher.externalId==userId } else emptyList()
        val enrolled=if(UserRole.STUDENT in roles) academicProvider.getAcademicLoad(userId).enrollments.map { it.course } else emptyList()
        taught+enrolled
    }

    private suspend fun <T> translateIntegrationFailure(block: suspend () -> T): T =
        try {
            block()
        } catch (error: IntegrationException) {
            when (error.category) {
                IntegrationException.Category.NOT_FOUND ->
                    throw ApiException.NotFound("Academic record was not found")
                IntegrationException.Category.UNAUTHORIZED ->
                    throw ApiException.Unauthorized("Institutional session is no longer valid")
                IntegrationException.Category.UNAVAILABLE,
                IntegrationException.Category.TIMEOUT,
                -> throw ApiException.DependencyUnavailable(
                    "The academic system is temporarily unavailable",
                    error,
                )
                IntegrationException.Category.BAD_REQUEST,
                IntegrationException.Category.MALFORMED_RESPONSE,
                -> throw ApiException.Internal(
                    "The academic system returned data we could not use",
                    error,
                )
            }
        }
}
