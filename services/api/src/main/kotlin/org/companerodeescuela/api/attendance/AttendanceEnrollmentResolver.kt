package org.companerodeescuela.api.attendance

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.AcademicProvider

class AttendanceEnrollmentResolver(
    private val academicProvider: AcademicProvider,
    private val classrooms: org.companerodeescuela.api.classroom.ClassroomService? = null,
    private val institutionalAttendance: SiiiAttendanceAuthority? = null,
) {
    suspend fun courseGroupsFor(
        studentId: String,
        nativeActor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): Set<Pair<String, String>> {
        if (nativeActor != null) {
            if (nativeActor.id != studentId ||
                org.companerodeescuela.shared.contracts.UserRole.STUDENT !in nativeActor.roles
            ) {
                throw ApiException.Forbidden("Actual native student is required")
            }
            institutionalAttendance?.takeIf { it.configured(nativeActor) }?.let { return it.studentGroups(nativeActor) }
            return (
                classrooms ?: throw ApiException.DependencyUnavailable(
                    "Native classroom authority is unavailable",
                )
            ).nativeChannelsFor(nativeActor)
                .map {
                    it.id to
                        it.groupName.orEmpty()
                }.toSet()
        }
        return academicLoad(studentId).enrollments.map { it.course.externalId to it.course.groupName.trim() }.toSet()
    }

    suspend fun isEnrolled(
        studentId: String,
        courseId: String,
        groupName: String,
        nativeActor: org.companerodeescuela.shared.contracts.UserSummary? = null,
    ): Boolean = (courseId to groupName.trim()) in courseGroupsFor(studentId, nativeActor)

    private suspend fun academicLoad(studentId: String) =
        try {
            academicProvider.getAcademicLoad(studentId)
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
