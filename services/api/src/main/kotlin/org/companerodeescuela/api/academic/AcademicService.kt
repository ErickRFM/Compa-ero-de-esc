package org.companerodeescuela.api.academic

import java.time.LocalDate
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.academic.mapper.AcademicMappers
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.AcademicWeekResponse
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleSource
import org.companerodeescuela.shared.model.ClassOccurrence
import org.companerodeescuela.shared.model.ClassOccurrenceStatus
import org.companerodeescuela.shared.model.PersonId

/**
 * Converts institution-specific academic data into stable platform contracts.
 *
 * The legacy weekly schedule remains available for the current Android client.
 * The v2 weekly projection adds deterministic dated class occurrences so
 * attendance can bind to a real meeting instead of a loose course/group pair.
 */
class AcademicService(
    private val provider: AcademicProvider,
    private val scheduleOverrides: AcademicScheduleOverrideRepository? = null,
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

            val institutionalEntries = schedule.slots
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
                }
            val manualEntries = scheduleOverrides
                ?.listForOwner(externalId)
                .orEmpty()
                .map { managed ->
                    val block = managed.block
                    ScheduleEntry(
                        courseId = block.id,
                        subjectCode = block.subjectId ?: "MANUAL",
                        subjectName = block.subjectName,
                        groupName = block.groupName.orEmpty(),
                        teacherName = block.teacherName.orEmpty(),
                        dayOfWeek = block.dayOfWeek,
                        startsAt = block.startTime,
                        endsAt = block.endTime,
                        classroomName = block.room,
                        buildingName = null,
                        campusName = null,
                        source = ScheduleSource.MANUAL,
                    )
                }

            AcademicLoadResponse(
                student = AcademicProfile(
                    id = student.person.id.value,
                    displayName = student.person.displayName,
                    email = student.person.institutionalEmail,
                ),
                schedule = AcademicScheduleResponse(
                    ownerId = schedule.ownerId.value,
                    entries = mergeForPresentation(institutionalEntries, manualEntries),
                ),
            )
        }

    private fun mergeForPresentation(
        institutional: List<ScheduleEntry>,
        manual: List<ScheduleEntry>,
    ): List<ScheduleEntry> {
        val institutionalKeys = institutional.map(::entryKey).toSet()
        return (institutional + manual.filter { entryKey(it) !in institutionalKeys })
            .sortedWith(compareBy({ dayOrder(it.dayOfWeek) }, { it.startsAt }, { it.subjectName }))
    }

    private fun entryKey(entry: ScheduleEntry): String =
        listOf(
            entry.dayOfWeek.trim().uppercase(),
            entry.startsAt.trim(),
            entry.endsAt.trim(),
            entry.subjectName.trim().lowercase(),
        ).joinToString("|")

    private fun dayOrder(day: String): Int = when (day) {
        "MONDAY" -> 1
        "TUESDAY" -> 2
        "WEDNESDAY" -> 3
        "THURSDAY" -> 4
        "FRIDAY" -> 5
        "SATURDAY" -> 6
        "SUNDAY" -> 7
        else -> 8
    }

    suspend fun scheduleFor(externalId: String): AcademicScheduleResponse =
        loadFor(externalId).schedule

    suspend fun scheduleWeekFor(
        externalId: String,
        weekOf: LocalDate,
    ): AcademicWeekResponse =
        translateIntegrationFailure {
            val coursesById = provider.listCourses().associateBy { it.externalId }
            val schedule = AcademicMappers.toSchedule(
                ownerId = PersonId(externalId),
                slots = provider.getSchedule(externalId, weekOf),
                coursesById = coursesById,
            )
            val weekStart = AcademicOccurrenceProjection.weekStart(weekOf)
            val occurrences = AcademicOccurrenceProjection.project(schedule, weekOf)

            AcademicWeekResponse(
                ownerId = externalId,
                weekStartsOn = weekStart.toString(),
                weekEndsOn = weekStart.plusDays(6).toString(),
                occurrences = occurrences.map(::toContract),
            )
        }

    private fun toContract(occurrence: ClassOccurrence): ClassOccurrenceContract =
        ClassOccurrenceContract(
            id = occurrence.id.value,
            patternId = occurrence.patternId?.value,
            courseId = occurrence.group.course.id.value,
            groupName = occurrence.group.name,
            subjectCode = occurrence.group.course.subject.code,
            subjectName = occurrence.group.course.subject.name,
            teacherName = occurrence.teacher.person.displayName,
            date = occurrence.date.toString(),
            startsAt = occurrence.startsAt.toString(),
            endsAt = occurrence.endsAt.toString(),
            status = when (occurrence.status) {
                ClassOccurrenceStatus.SCHEDULED -> ClassOccurrenceStatusContract.SCHEDULED
                ClassOccurrenceStatus.CANCELLED -> ClassOccurrenceStatusContract.CANCELLED
                ClassOccurrenceStatus.RESCHEDULED -> ClassOccurrenceStatusContract.RESCHEDULED
                ClassOccurrenceStatus.ONLINE -> ClassOccurrenceStatusContract.ONLINE
            },
            classroomName = occurrence.classroom?.name,
            buildingName = occurrence.classroom?.building?.name,
            campusName = occurrence.classroom?.building?.campus?.name,
        )

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
