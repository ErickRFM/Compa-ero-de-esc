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
        val authorized=mutableListOf<TutorCaseSummary>()
        for(record in records) {
            try { requireCaseScope(actor,record);authorized+=record.toSummary() } catch(_:ApiException.Forbidden) { /* Old or different-period records remain retained and private. */ }
        }
        return authorized
    }

    suspend fun detail(actor: UserSummary, id: String): TutorCaseSummary {
        val record = repository.find(id) ?: throw ApiException.NotFound("Tutor case not found")
        requireCaseScope(actor,record)
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
        try { assignments.requireRepresentativeCandidate(groupId,studentId) }
        catch(_:ApiException.Validation) { throw ApiException.Forbidden("Case student must be verified and active in this group institution") }
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
            institutionId=group.institutionId,
            groupScopeRevision=group.scopeRevision,
        )).toSummary()
    }

    suspend fun addNote(
        actor: UserSummary,
        id: String,
        request: AddTutorCaseNoteRequest,
    ): TutorCaseSummary {
        val current = repository.find(id) ?: throw ApiException.NotFound("Tutor case not found")
        requireCaseScope(actor,current)
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
        val student=assignments.liveStudent(actor)
        val own=mutableListOf<TutorCaseRecord>()
        for(record in repository.listForStudent(student.id)) {
            val group=groups.find(record.academicGroupId)
            if(record.institutionId!=null && record.institutionId==student.institutionId && group?.institutionId==record.institutionId && record.groupScopeRevision>=0 && record.studentId==student.id) own+=record
        }
        return own.flatMap { record ->
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

    private suspend fun requireCaseScope(actor:UserSummary,record:TutorCaseRecord) {
        requireScope(actor,record.academicGroupId)
        val group=groups.find(record.academicGroupId) ?: throw ApiException.Forbidden("Actual case group is required")
        if(record.institutionId==null || record.institutionId!=group.institutionId || record.groupScopeRevision<0 || record.groupScopeRevision!=group.scopeRevision || record.version<0) throw ApiException.Forbidden("Case has unknown or stale native scope; explicit review is required")
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
