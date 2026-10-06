package org.companerodeescuela.api.tutoring

import java.time.Clock
import java.util.UUID
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.CreateTutorAssignmentRequest
import org.companerodeescuela.shared.contracts.TutorAssignmentSummary
import org.companerodeescuela.shared.contracts.TutorScopeSummary
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class TutorAssignmentService(
    private val repository: TutorAssignmentRepository,
    private val groupRepository: AcademicGroupRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun listFor(actor: UserSummary): List<TutorAssignmentSummary> {
        val records = if (isAdmin(actor)) {
            repository.listAll()
        } else {
            requireTutor(actor)
            repository.listForTutor(actor.id)
        }
        return records.mapNotNull { record ->
            val group = groupRepository.find(record.academicGroupId) ?: return@mapNotNull null
            TutorAssignmentSummary(
                id = record.id,
                tutorUserId = record.tutorUserId,
                academicGroupId = group.id,
                academicGroupName = group.name,
                active = record.active,
                assignedBy = record.assignedBy,
                assignedAtEpochSeconds = record.assignedAt.epochSecond,
            )
        }
    }

    suspend fun create(
        actor: UserSummary,
        request: CreateTutorAssignmentRequest,
    ): TutorAssignmentSummary {
        requireAdmin(actor)
        val tutorUserId = request.tutorUserId.trim().takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("tutorUserId is required")
        val academicGroupId = request.academicGroupId.trim().uppercase()
            .takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("academicGroupId is required")
        val group = groupRepository.find(academicGroupId)
            ?: throw ApiException.NotFound("Academic group was not found")
        val record = repository.create(
            TutorAssignmentRecord(
                id = UUID.randomUUID().toString(),
                tutorUserId = tutorUserId,
                academicGroupId = group.id,
                active = true,
                assignedBy = actor.id,
                assignedAt = clock.instant(),
            ),
        )
        return TutorAssignmentSummary(
            id = record.id,
            tutorUserId = record.tutorUserId,
            academicGroupId = group.id,
            academicGroupName = group.name,
            active = record.active,
            assignedBy = record.assignedBy,
            assignedAtEpochSeconds = record.assignedAt.epochSecond,
        )
    }

    suspend fun scopeFor(actor: UserSummary): TutorScopeSummary {
        requireTutor(actor)
        val groups = repository.listForTutor(actor.id)
            .mapNotNull { groupRepository.find(it.academicGroupId) }
            .filter { it.active }
            .map { AcademicGroupSummary(id = it.id, name = it.name, active = it.active) }
        return TutorScopeSummary(tutorUserId = actor.id, groups = groups)
    }

    suspend fun requireCanAccessGroup(actor: UserSummary, academicGroupId: String) {
        if (isAdmin(actor)) return
        requireTutor(actor)
        if (repository.findActive(actor.id, academicGroupId) == null) {
            throw ApiException.Forbidden("Tutor is not assigned to this academic group")
        }
    }

    private fun requireTutor(actor: UserSummary) {
        if (UserRole.TUTOR !in actor.roles) {
            throw ApiException.Forbidden("Tutor role is required")
        }
    }

    private fun requireAdmin(actor: UserSummary) {
        if (!isAdmin(actor)) {
            throw ApiException.Forbidden("Academic administration permission is required")
        }
    }

    private fun isAdmin(actor: UserSummary): Boolean =
        UserRole.ADMIN in actor.roles || UserRole.SUPER_ADMIN in actor.roles
}
