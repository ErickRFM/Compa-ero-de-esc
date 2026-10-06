package org.companerodeescuela.api.excuses

import java.time.Clock
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.tutoring.TutorAssignmentService
import org.companerodeescuela.shared.contracts.ExcuseRequestSummary
import org.companerodeescuela.shared.contracts.ExcuseStatus
import org.companerodeescuela.shared.contracts.ReviewExcuseRequest
import org.companerodeescuela.shared.contracts.SubmitExcuseRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class ExcuseService(
    private val repository: ExcuseRepository,
    private val tutoring: TutorAssignmentService,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun submit(
        actor: UserSummary,
        request: SubmitExcuseRequest,
    ): ExcuseRequestSummary {
        if (UserRole.STUDENT !in actor.roles) {
            throw ApiException.Forbidden("Only students can submit excuses")
        }
        val groupId = request.academicGroupId.trim().uppercase()
            .takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("academicGroupId is required")
        val date = request.attendanceDateIso.trim()
            .takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("attendanceDateIso is required")
        val reason = request.reason.trim()
        if (reason.length !in 5..1000) {
            throw ApiException.Validation("reason must contain between 5 and 1000 characters")
        }

        val record = ExcuseRecord(
            id = UUID.randomUUID().toString(),
            studentId = actor.id,
            academicGroupId = groupId,
            attendanceDateIso = date,
            reason = reason,
            attachmentRefs = request.attachmentRefs.map(String::trim).filter(String::isNotBlank).take(5),
            status = ExcuseStatus.PENDING,
            submittedAt = clock.instant(),
        )
        repository.create(record)
        return record.toSummary()
    }

    suspend fun listFor(actor: UserSummary): List<ExcuseRequestSummary> =
        when {
            UserRole.STUDENT in actor.roles ->
                repository.listForStudent(actor.id).map(ExcuseRecord::toSummary)

            isAdmin(actor) ->
                repository.listAll().map(ExcuseRecord::toSummary)

            UserRole.TUTOR in actor.roles ->
                repository.listAll().filter { record ->
                    runCatching {
                        tutoring.requireCanAccessGroup(actor, record.academicGroupId)
                        true
                    }.getOrDefault(false)
                }.map(ExcuseRecord::toSummary)

            else -> throw ApiException.Forbidden("Excuse access is not allowed for this account")
        }

    suspend fun review(
        actor: UserSummary,
        excuseId: String,
        request: ReviewExcuseRequest,
    ): ExcuseRequestSummary {
        val current = repository.find(excuseId)
            ?: throw ApiException.NotFound("Excuse request was not found")

        if (!isAdmin(actor)) {
            tutoring.requireCanAccessGroup(actor, current.academicGroupId)
        }

        if (current.status !in setOf(ExcuseStatus.PENDING, ExcuseStatus.UNDER_REVIEW)) {
            throw ApiException.Conflict("Excuse request is already finalized")
        }
        if (request.status !in setOf(ExcuseStatus.APPROVED, ExcuseStatus.REJECTED, ExcuseStatus.UNDER_REVIEW)) {
            throw ApiException.Validation("Invalid review status")
        }

        val updated = current.copy(
            status = request.status,
            reviewedAt = clock.instant(),
            reviewedBy = actor.id,
            reviewComment = request.comment?.trim()?.takeIf(String::isNotBlank)?.take(1000),
        )
        return repository.update(updated).toSummary()
    }

    private fun isAdmin(actor: UserSummary): Boolean =
        UserRole.ADMIN in actor.roles || UserRole.SUPER_ADMIN in actor.roles

    private fun ExcuseRecord.toSummary(): ExcuseRequestSummary =
        ExcuseRequestSummary(
            id = id,
            studentId = studentId,
            academicGroupId = academicGroupId,
            attendanceDateIso = attendanceDateIso,
            reason = reason,
            attachmentRefs = attachmentRefs,
            status = status,
            submittedAtEpochSeconds = submittedAt.epochSecond,
            reviewedAtEpochSeconds = reviewedAt?.epochSecond,
            reviewedBy = reviewedBy,
            reviewComment = reviewComment,
        )
}
