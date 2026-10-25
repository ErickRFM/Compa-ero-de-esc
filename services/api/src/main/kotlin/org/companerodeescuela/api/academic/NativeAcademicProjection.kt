package org.companerodeescuela.api.academic

import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.admin.InstitutionScopePolicy
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AcademicLoadResponse
import org.companerodeescuela.shared.contracts.AcademicScheduleResponse
import org.companerodeescuela.shared.contracts.AcademicWeekResponse
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.BlockStatus
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract
import org.companerodeescuela.shared.contracts.ManagedScheduleBlock
import org.companerodeescuela.shared.contracts.ScheduleEntry
import org.companerodeescuela.shared.contracts.ScheduleRecurrence
import org.companerodeescuela.shared.contracts.ScheduleSource
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary
import org.companerodeescuela.shared.model.AcademicId
import org.companerodeescuela.shared.contracts.AcademicProfile
import org.companerodeescuela.shared.model.ClassOccurrence
import org.companerodeescuela.shared.model.ClassOccurrenceStatus
import org.companerodeescuela.shared.model.Course
import org.companerodeescuela.shared.model.Group
import org.companerodeescuela.shared.model.Person
import org.companerodeescuela.shared.model.PersonId
import org.companerodeescuela.shared.model.Schedule
import org.companerodeescuela.shared.model.ScheduleSlot
import org.companerodeescuela.shared.model.Subject
import org.companerodeescuela.shared.model.Teacher
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/** Native identity and assigned schedules stay separate from provider identities. */
class NativeAcademicProjection(
    private val accounts: PlatformAccountRepository,
    private val classrooms: ClassroomService,
    private val groups: AcademicGroupRepository,
    private val schedules: AcademicScheduleOverrideRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val policy = InstitutionScopePolicy(accounts, clock)

    private data class Meeting(
        val managed: ManagedScheduleBlock,
        val group: org.companerodeescuela.api.academic.groups.AcademicGroupRecord,
        val course: Course,
    )

    private suspend fun meetings(actor: UserSummary): List<Meeting> {
        val current = policy.live(actor)
        if (current.roles.none { it == UserRole.STUDENT || it == UserRole.TEACHER }) {
            throw ApiException.Forbidden("An actual native student or teacher is required")
        }
        val result = mutableListOf<Meeting>()
        for (classroom in classrooms.nativeChannelsFor(actor)) {
            val group = classroom.groupId?.let { groups.find(it) } ?: continue
            val teacher = accounts.findById(classroom.teacherId) ?: continue
            // A revoked/regranted or inactive teacher cannot keep an old class authoritative.
            try {
                classrooms.requireCanPublish(teacher.toUserSummary(), classroom.id)
            } catch (
                _: ApiException.Forbidden,
            ) {
                continue
            } catch (_: ApiException.Unauthorized) {
                continue
            }
            val modelTeacher = Teacher(Person(PersonId(teacher.id), teacher.displayName, teacher.email))
            val course =
                Course(
                    AcademicId(classroom.id),
                    Subject(AcademicId(classroom.id), classroom.id, classroom.name),
                    modelTeacher,
                    group.periodId ?: continue,
                )
            val records = (schedules.listForOwner(group.id) + schedules.listForOwner(current.id)).distinctBy { it.block.id }
            for (record in records) {
                val block = record.block
                if (record.institutionId != group.institutionId ||
                    record.groupScopeRevision != group.scopeRevision ||
                    record.revision <= 0 ||
                    record.operationId.isNullOrBlank() ||
                    record.operationDigest.isNullOrBlank() ||
                    block.groupId != group.id ||
                    block.subjectId != classroom.id ||
                    block.teacherId != teacher.id
                ) {
                    continue
                }
                val start = runCatching { LocalTime.parse(block.startTime) }.getOrNull() ?: continue
                val end = runCatching { LocalTime.parse(block.endTime) }.getOrNull() ?: continue
                if (start >= end || runCatching { DayOfWeek.valueOf(block.dayOfWeek) }.isFailure) continue
                if (block.recurrence != ScheduleRecurrence.WEEKLY &&
                    runCatching { LocalDate.parse(block.effectiveDate) }.isFailure
                ) {
                    continue
                }
                result += Meeting(record, group, course)
            }
        }
        return result.distinctBy { it.managed.block.id }
    }

    suspend fun load(actor: UserSummary): AcademicLoadResponse {
        val current = policy.live(actor)
        val entries =
            meetings(actor)
                .filter { it.managed.block.status != BlockStatus.CANCELLED }
                .map { meeting ->
                    val b = meeting.managed.block
                    ScheduleEntry(
                        meeting.course.id.value,
                        meeting.course.subject.code,
                        meeting.course.subject.name,
                        meeting.group.name,
                        meeting.course.teacher.person.displayName,
                        b.dayOfWeek,
                        b.startTime,
                        b.endTime,
                        b.room,
                        source = ScheduleSource.MANUAL,
                        recurrence = b.recurrence,
                        seriesId = b.seriesId,
                        effectiveDate = b.effectiveDate,
                    )
                }.sortedWith(compareBy({ DayOfWeek.valueOf(it.dayOfWeek).value }, { it.startsAt }, { it.subjectName }))
        return AcademicLoadResponse(
            AcademicProfile(current.id, current.displayName, current.email),
            AcademicScheduleResponse(current.id, entries),
        )
    }

    private fun project(
        meeting: Meeting,
        date: LocalDate,
    ): ClassOccurrence? {
        val b = meeting.managed.block
        val start = AcademicOccurrenceProjection.weekStart(date)
        val actual =
            if (b.recurrence == ScheduleRecurrence.WEEKLY) {
                start.plusDays(DayOfWeek.valueOf(b.dayOfWeek).value.toLong() - 1)
            } else {
                LocalDate.parse(b.effectiveDate).takeIf { it >= start && it <= start.plusDays(6) } ?: return null
            }
        val starts = actual.atTime(LocalTime.parse(b.startTime)).toInstant(ZoneOffset.UTC)
        val ends = actual.atTime(LocalTime.parse(b.endTime)).toInstant(ZoneOffset.UTC)
        if (meeting.group.startsAt == null ||
            meeting.group.endsAt == null ||
            starts < meeting.group.startsAt ||
            ends > meeting.group.endsAt
        ) {
            return null
        }
        val group = Group(AcademicId(meeting.group.id), meeting.course, meeting.group.name)
        val schedule =
            Schedule(
                PersonId(meeting.course.teacher.person.id.value),
                listOf(ScheduleSlot(group, actual.dayOfWeek, LocalTime.parse(b.startTime), LocalTime.parse(b.endTime), null)),
            )
        val projected = AcademicOccurrenceProjection.project(schedule, actual).single()
        val pattern =
            "native-pattern-" +
                UUID.nameUUIDFromBytes(
                    (meeting.group.institutionId + "\u0000" + meeting.group.scopeRevision + "\u0000" + b.id).toByteArray(Charsets.UTF_8),
                )
        val id = "native-occurrence-" + UUID.nameUUIDFromBytes((pattern + "\u0000" + actual).toByteArray(Charsets.UTF_8))
        return projected.copy(
            id = AcademicId(id),
            patternId = AcademicId(pattern),
            date = actual,
            status =
                when (b.status) {
                    BlockStatus.CANCELLED -> ClassOccurrenceStatus.CANCELLED
                    BlockStatus.RESCHEDULED, BlockStatus.ROOM_CHANGED -> ClassOccurrenceStatus.RESCHEDULED
                    else -> ClassOccurrenceStatus.SCHEDULED
                },
        )
    }

    private suspend fun projected(
        actor: UserSummary,
        date: LocalDate,
    ) = meetings(actor)
        .mapNotNull { meeting ->
            project(meeting, date)?.let {
                it to
                    meeting.managed.block.room
            }
        }.sortedWith(compareBy({ it.first.date }, { it.first.startsAt }))

    suspend fun week(
        actor: UserSummary,
        date: LocalDate,
    ): AcademicWeekResponse {
        val start = AcademicOccurrenceProjection.weekStart(date)
        return AcademicWeekResponse(
            actor.id,
            start.toString(),
            start.plusDays(6).toString(),
            projected(actor, date).map { (o, room) ->
                ClassOccurrenceContract(
                    o.id.value,
                    o.patternId?.value,
                    o.group.course.id.value,
                    o.group.name,
                    o.group.course.subject.code,
                    o.group.course.subject.name,
                    o.teacher.person.displayName,
                    o.date.toString(),
                    o.startsAt.toString(),
                    o.endsAt.toString(),
                    when (o.status) {
                        ClassOccurrenceStatus.CANCELLED -> ClassOccurrenceStatusContract.CANCELLED
                        ClassOccurrenceStatus.RESCHEDULED -> ClassOccurrenceStatusContract.RESCHEDULED
                        ClassOccurrenceStatus.ONLINE -> ClassOccurrenceStatusContract.ONLINE
                        ClassOccurrenceStatus.SCHEDULED -> ClassOccurrenceStatusContract.SCHEDULED
                    },
                    classroomName = room,
                )
            },
        )
    }

    suspend fun teacherOccurrence(
        actor: UserSummary,
        id: String,
        date: LocalDate,
    ): ClassOccurrence {
        val current = policy.live(actor)
        if (UserRole.TEACHER !in current.roles) throw ApiException.Forbidden("Current native teacher is required")
        val occurrence =
            projected(actor, date).firstOrNull { it.first.id.value == id && it.first.date == date }?.first
                ?: throw ApiException.NotFound("Actual native class occurrence was not found")
        if (occurrence.teacher.person.id.value != current.id) throw ApiException.Forbidden("This class is assigned to another teacher")
        if (occurrence.status == ClassOccurrenceStatus.CANCELLED) {
            throw ApiException.Conflict(
                "Attendance cannot open for a cancelled class",
            )
        }
        return occurrence
    }

    suspend fun studentIdsForTeacher(
        actor: UserSummary,
        classroomId: String,
    ): List<String> = classrooms.studentIdsForTeacher(actor, classroomId)

    suspend fun activeStudentIdsForTeacher(
        actor: UserSummary,
        classroomId: String,
    ): List<String> = classrooms.activeStudentIdsForTeacher(actor, classroomId)

    suspend fun bindSession(
        actor: UserSummary,
        session: AttendanceSessionResponse,
    ): AttendanceSessionResponse {
        policy.live(actor)
        val classroom = classrooms.requireCanPublish(actor, session.courseId)
        val group = classroom.groupId?.let { groups.find(it) } ?: throw ApiException.Forbidden("Actual class group is unavailable")
        if (classroom.teacherId != actor.id ||
            session.openedBy != actor.id
        ) {
            throw ApiException.Forbidden("The assigned teacher must open this session")
        }
        return session.copy(
            identitySource = AttendanceIdentitySource.NATIVE,
            institutionId = group.institutionId,
            groupId = group.id,
            groupScopeRevision = group.scopeRevision,
        )
    }

    suspend fun requireSession(
        actor: UserSummary,
        session: AttendanceSessionResponse,
    ) {
        val current = policy.live(actor)
        if (session.identitySource != AttendanceIdentitySource.NATIVE ||
            session.institutionId != current.institutionId ||
            session.groupScopeRevision < 0
        ) {
            throw ApiException.Forbidden("Attendance source or institution does not match the actual native identity")
        }
        val classroom = classrooms.requireCanRead(actor, session.courseId)
        val group = classroom.groupId?.let { groups.find(it) } ?: throw ApiException.Forbidden("Actual class group is unavailable")
        if (session.groupId != group.id ||
            session.groupScopeRevision != group.scopeRevision ||
            session.openedBy != classroom.teacherId ||
            session.groupName != group.name
        ) {
            throw ApiException.Forbidden("Attendance class scope is stale or inconsistent")
        }
        val date =
            runCatching {
                LocalDate.parse(session.occurrenceDate)
            }.getOrElse { throw ApiException.Forbidden("Actual occurrence date is required") }
        val occurrence =
            week(actor, date).occurrences.firstOrNull {
                it.id == session.occurrenceId &&
                    it.date == session.occurrenceDate &&
                    it.courseId == session.courseId
            }
                ?: throw ApiException.Forbidden("Attendance occurrence is no longer assigned")
        if (occurrence.status == ClassOccurrenceStatusContract.CANCELLED ||
            occurrence.startsAt != session.scheduledStartsAt ||
            occurrence.endsAt != session.scheduledEndsAt
        ) {
            throw ApiException.Forbidden("Attendance occurrence has changed or been cancelled")
        }
    }
}
