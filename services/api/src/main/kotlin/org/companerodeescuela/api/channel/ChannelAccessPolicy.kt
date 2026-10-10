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
) {
    suspend fun channelsFor(userId: String, roles: Set<UserRole>): List<ClassChannelSummary> {
        val native = classroomService
            ?.nativeChannelsFor(
                UserSummary(
                    id = userId,
                    displayName = userId,
                    roles = roles,
                ),
            )
            .orEmpty()
            .map { classroom ->
                ClassChannelSummary(
                    id = classroom.id,
                    courseId = classroom.id,
                    subjectCode = "LOCAL",
                    subjectName = classroom.name,
                    groupName = classroom.groupName ?: "Clase",
                    term = "Compañero",
                    teacherId = classroom.teacherId,
                    teacherDisplayName = classroom.teacherDisplayName,
                    canPublish = classroom.canManage,
                    channelType = ChannelType.CLASS,
                )
            }

        val courses = try {
            accessibleCourses(userId, roles)
        } catch (error: ApiException) {
            if (
                native.isNotEmpty() &&
                (
                    error is ApiException.NotFound ||
                        error is ApiException.DependencyUnavailable ||
                        error is ApiException.Unauthorized
                )
            ) {
                emptyList()
            } else {
                throw error
            }
        }
        val institutional = courses
            .distinctBy(ExternalCourse::externalId)
            .sortedWith(compareBy({ it.subject.name }, { it.groupName }))
            .map { course ->
                ClassChannelSummary(
                    id = course.externalId,
                    courseId = course.externalId,
                    subjectCode = course.subject.externalCode,
                    subjectName = course.subject.name,
                    groupName = course.groupName,
                    term = course.term,
                    teacherId = course.teacher.externalId,
                    teacherDisplayName = course.teacher.fullName,
                    canPublish = canPublishCourse(userId, roles, course),
                    channelType = ChannelType.CLASS,
                )
            }

        val repChannel = if (canAccessRepresentativesChannel(userId, roles)) {
            listOf(
                ClassChannelSummary(
                    id = "channel-representatives",
                    courseId = "representatives",
                    subjectCode = "INST",
                    subjectName = "Representantes de Grupo",
                    groupName = "Institucional",
                    term = "Compañero",
                    teacherId = "admin",
                    teacherDisplayName = "Administración Institucional",
                    canPublish = roles.any(UserRole::isAdministrative),
                    channelType = ChannelType.REPRESENTATIVES,
                ),
            )
        } else {
            emptyList()
        }

        return (native + institutional + repChannel).distinctBy(ClassChannelSummary::id)
    }

    suspend fun requireCanRead(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
    ): ClassChannelSummary =
        channelsFor(userId, roles).firstOrNull { it.id == channelId }
            ?: throw ApiException.Forbidden("This class channel is not assigned to your account")

    suspend fun requireCanPublish(
        userId: String,
        roles: Set<UserRole>,
        channelId: String,
    ): ClassChannelSummary {
        val channel = requireCanRead(userId, roles, channelId)
        if (!channel.canPublish) {
            throw ApiException.Forbidden("Only assigned teachers or administrators can publish")
        }
        return channel
    }

    private suspend fun canAccessRepresentativesChannel(
        userId: String,
        roles: Set<UserRole>,
    ): Boolean {
        if (roles.any(UserRole::isAdministrative)) return true
        if (UserRole.TEACHER in roles) return true
        if (representativeService != null) {
            val active = representativeService.activeAssignmentsForStudent(userId)
            if (active.isNotEmpty()) return true
        }
        return false
    }

    private suspend fun accessibleCourses(
        userId: String,
        roles: Set<UserRole>,
    ): List<ExternalCourse> = translateIntegrationFailure {
        when {
            roles.any(UserRole::isAdministrative) -> academicProvider.listCourses()
            UserRole.TEACHER in roles && UserRole.STUDENT in roles -> {
                val taught = academicProvider.listCourses()
                    .filter { it.teacher.externalId == userId }
                val enrolled = academicProvider.getAcademicLoad(userId)
                    .enrollments
                    .map { it.course }
                taught + enrolled
            }
            UserRole.TEACHER in roles -> academicProvider.listCourses()
                .filter { it.teacher.externalId == userId }
            UserRole.STUDENT in roles -> academicProvider.getAcademicLoad(userId)
                .enrollments
                .map { it.course }
            else -> emptyList()
        }
    }

    private fun canPublishCourse(
        userId: String,
        roles: Set<UserRole>,
        course: ExternalCourse,
    ): Boolean =
        roles.any(UserRole::isAdministrative) ||
            (UserRole.TEACHER in roles && course.teacher.externalId == userId)

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
