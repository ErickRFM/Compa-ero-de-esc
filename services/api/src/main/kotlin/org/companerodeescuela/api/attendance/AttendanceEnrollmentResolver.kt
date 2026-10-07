package org.companerodeescuela.api.attendance

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.AcademicProvider

class AttendanceEnrollmentResolver(
    private val academicProvider: AcademicProvider,
) {
    suspend fun courseGroupsFor(studentId: String): Set<Pair<String, String>> =
        academicLoad(studentId).enrollments
            .map { it.course.externalId to it.course.groupName.trim() }
            .toSet()

    suspend fun isEnrolled(
        studentId: String,
        courseId: String,
        groupName: String,
    ): Boolean = (courseId to groupName.trim()) in courseGroupsFor(studentId)

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
