package org.companerodeescuela.api.tutoring

import java.time.Clock
import java.util.UUID
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AddTutorCaseNoteRequest
import org.companerodeescuela.shared.contracts.CreateTutorCaseRequest
import org.companerodeescuela.shared.contracts.StudentTutorNoteSummary
import org.companerodeescuela.shared.contracts.TutorCaseNoteSummary
import org.companerodeescuela.shared.contracts.TutorCaseStatus
import org.companerodeescuela.shared.contracts.TutorCaseSummary
import org.companerodeescuela.shared.contracts.TutorNoteVisibility
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

/**
 * Case summaries are for authorized staff only. Students see only explicitly
 * published notes through their separate, subject-bound endpoint.
 */
class TutorCaseService(
    private val repository: TutorCaseRepository,
    private val assignments: TutorAssignmentService,
    private val groups: AcademicGroupRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun list(actor: UserSummary): List<TutorCaseSummary> {
        val groupIds = assignments.scopeFor(actor).groups.map { it.id }.toSet()
        val records = repository.listForGroups(groupIds)
        return records.map { it.toSummary() }
    }

    suspend fun detail(actor: UserSummary, id: String): TutorCaseSummary {
        val record = repository.find(id) ?: throw ApiException.NotFound("Tutor case not found")
        requireScope(actor, record.academicGroupId)
        return record.toSummary()
    }

    suspend fun create(actor: UserSummary, request: CreateTutorCaseRequest): TutorCaseSummary {
        val groupId = request.academicGroupId.trim().uppercase()
        val studentId = request.studentId.trim()
        val summary = request.summary.trim()
        if (groupId.isEmpty() || studentId.isEmpty() || summary.length !in 8..500) {
            throw ApiException.Validation("Group, student and summary (8-500 characters) are required")
        }
        requireScope(actor, groupId)
        val group = groups.find(groupId)
        if (group?.active != true || !groups.isUserInGroup(studentId, groupId)) {
            throw ApiException.Forbidden("Student is not enrolled in this active group")
        }
        val now = clock.instant()
        return repository.create(TutorCaseRecord(
            id = UUID.randomUUID().toString(),
            academicGroupId = groupId,
            studentId = studentId,
            summary = summary,
            status = TutorCaseStatus.OPEN,
            createdBy = actor.id,
            createdAt = now,
            updatedAt = now,
        )).toSummary()
    }

    suspend fun addNote(
        actor: UserSummary,
        id: String,
        request: AddTutorCaseNoteRequest,
    ): TutorCaseSummary {
        val current = repository.find(id) ?: throw ApiException.NotFound("Tutor case not found")
        requireScope(actor, current.academicGroupId)
        if (current.status == TutorCaseStatus.CLOSED) {
            throw ApiException.Conflict("Closed cases cannot accept new notes")
        }
        // Sensitive/coordinator records require separate explicit authorizations.
        if (request.visibility !in setOf(TutorNoteVisibility.TUTOR_INTERNAL, TutorNoteVisibility.STUDENT_VISIBLE)) {
            throw ApiException.Forbidden("Requested note visibility is not enabled")
        }
        val body = request.body.trim()
        if (body.length !in 5..2000) throw ApiException.Validation("Note must contain 5-2000 characters")
        val now = clock.instant()
        val note = TutorCaseNoteRecord(
            id = UUID.randomUUID().toString(),
            body = body,
            visibility = request.visibility,
            authorId = actor.id,
            createdAt = now,
        )
        val next = current.copy(
            notes = current.notes + note, updatedAt = now, version = current.version + 1,
        )
        return (repository.compareAndUpdate(current, next)
            ?: throw ApiException.Conflict("Case changed; reload and retry")).toSummary()
    }

    suspend fun myPublishedNotes(actor: UserSummary): List<StudentTutorNoteSummary> {
        if (UserRole.STUDENT !in actor.roles) {
            throw ApiException.Forbidden("Student role is required")
        }
        return repository.listForStudent(actor.id).flatMap { record ->
            record.notes.filter { it.visibility == TutorNoteVisibility.STUDENT_VISIBLE }.map { note ->
                StudentTutorNoteSummary(
                    id = note.id,
                    academicGroupId = record.academicGroupId,
                    body = note.body,
                    authorId = note.authorId,
                    createdAtEpochSeconds = note.createdAt.epochSecond,
                )
            }
        }
    }

    private suspend fun requireScope(actor: UserSummary, groupId: String) {
        assignments.requireTutorAssignmentForGroup(actor, groupId)
    }

    private fun TutorCaseRecord.toSummary(): TutorCaseSummary = TutorCaseSummary(
        id = id,
        academicGroupId = academicGroupId,
        studentId = studentId,
        summary = summary,
        status = status,
        createdBy = createdBy,
        createdAtEpochSeconds = createdAt.epochSecond,
        updatedAtEpochSeconds = updatedAt.epochSecond,
        notes = notes.map { TutorCaseNoteSummary(
            id = it.id,
            body = it.body,
            visibility = it.visibility,
            authorId = it.authorId,
            createdAtEpochSeconds = it.createdAt.epochSecond,
        ) },
        version = version,
    )
}
