package org.companerodeescuela.api.attendance

import java.time.LocalDate
import org.companerodeescuela.api.academic.AcademicOccurrenceProjection
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.academic.mapper.AcademicMappers
import org.companerodeescuela.shared.model.ClassOccurrence
import org.companerodeescuela.shared.model.PersonId

interface AttendanceOccurrenceResolver {
    suspend fun resolveTeacherOccurrence(
        teacherId: String,
        occurrenceId: String,
        occurrenceDate: LocalDate,
    ): ClassOccurrence
}

class ProviderAttendanceOccurrenceResolver(
    private val provider: AcademicProvider,
) : AttendanceOccurrenceResolver {

    override suspend fun resolveTeacherOccurrence(
        teacherId: String,
        occurrenceId: String,
        occurrenceDate: LocalDate,
    ): ClassOccurrence = translateIntegrationFailure {
        val courses = provider.listCourses().associateBy { it.externalId }
        val schedule = AcademicMappers.toSchedule(
            ownerId = PersonId(teacherId),
            slots = provider.getSchedule(teacherId, occurrenceDate),
            coursesById = courses,
        )
        val occurrence = AcademicOccurrenceProjection
            .project(schedule, occurrenceDate)
            .firstOrNull { it.id.value == occurrenceId }
            ?: throw ApiException.NotFound("Class occurrence was not found")

        if (occurrence.teacher.person.id.value != teacherId) {
            throw ApiException.Forbidden("This class occurrence belongs to another teacher")
        }
        occurrence
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
