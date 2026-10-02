package org.companerodeescuela.api.academic

import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.academic.mapper.AcademicMappers
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.model.PersonId

/**
 * Converts one institution-specific academic load into the platform contract.
 */
class AcademicService(
    private val provider: AcademicProvider,
) {
    suspend fun loadFor(externalId: String): AcademicLoadResponse =
        translateIntegrationFailure {
            val load = provider.getAcademicLoad(externalId)
            val student = AcademicMappers.toStudent(load.student)
            val coursesById = load.enrollments.associate { enrollment ->
                enrollment.course.externalId to enrollment.course
            }
            val schedule = AcademicMappers.toSchedule(
                ownerId = PersonId(externalId),
                slots = load.schedule,
                coursesById = coursesById,
            )

            AcademicLoadResponse(
                student = AcademicProfile(
                    id = student.person.id.value,
                    displayName = student.person.displayName,
                    email = student.person.institutionalEmail,
                ),
                schedule = AcademicScheduleResponse(
                    ownerId = schedule.ownerId.value,
                    entries = schedule.slots
                        .sortedWith(compareBy({ it.dayOfWeek.value }, { it.startsAt }))
                        .map { slot ->
                            ScheduleEntry(
                                courseId = slot.group.course.id.value,
                                subjectCode = slot.group.course.subject.code,
                                subjectName = slot.group.course.subject.name,
                                groupName = slot.group.name,
                                teacherName = slot.group.course.teacher.person.displayName,
                                dayOfWeek = slot.dayOfWeek.name,
                                startsAt = slot.startsAt.toString(),
                                endsAt = slot.endsAt.toString(),
                                classroomName = slot.classroom?.name,
                                buildingName = slot.classroom?.building?.name,
                                campusName = slot.classroom?.building?.campus?.name,
                            )
                        },
                ),
            )
        }

    suspend fun scheduleFor(externalId: String): AcademicScheduleResponse =
        loadFor(externalId).schedule

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
