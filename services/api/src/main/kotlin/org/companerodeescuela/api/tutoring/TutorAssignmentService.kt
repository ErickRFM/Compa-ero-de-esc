package org.companerodeescuela.api.tutoring

import java.time.Clock
import java.util.UUID
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.CreateTutorAssignmentRequest
import org.companerodeescuela.shared.contracts.TutorAssignmentSummary
import org.companerodeescuela.shared.contracts.TutorScopeSummary
import org.companerodeescuela.shared.contracts.TutorStudentSummary
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

/**
 * Tutors are appointed by administration, not by teachers or self-registration.
 * Account validation is fail-closed until an identity repository is provided.
 * Institutional-only identities require an explicit adapter before appointment.
 */
class TutorAssignmentService(
    private val repository: TutorAssignmentRepository,
    private val groupRepository: AcademicGroupRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val accounts: PlatformAccountRepository? = null,
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
            record.toSummary(group.name)
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
        if (!group.active) throw ApiException.Conflict("Academic group is inactive")

        val accountRepository = accounts
            ?: throw ApiException.DependencyUnavailable("Tutor identity lookup is not configured")
        val target = accountRepository.findById(tutorUserId)
            ?: throw ApiException.NotFound("Tutor account was not found")
        if (!target.active || UserRole.TUTOR !in target.roles) {
            throw ApiException.Validation("An active account with the tutor role is required")
        }

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
        return record.toSummary(group.name)
    }

    suspend fun revoke(actor: UserSummary, assignmentId: String): TutorAssignmentSummary {
        requireAdmin(actor)
        val id = assignmentId.trim().takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("assignmentId is required")
        val updated = repository.revoke(id, actor.id, clock.instant())
            ?: throw ApiException.NotFound("Tutor assignment was not found")
        val groupName = groupRepository.find(updated.academicGroupId)?.name
            ?: updated.academicGroupId
        return updated.toSummary(groupName)
    }

    suspend fun studentsForGroup(actor: UserSummary, rawGroupId: String): List<TutorStudentSummary> {
        val groupId = rawGroupId.trim().uppercase()
        if (groupId.isBlank()) throw ApiException.Validation("Group ID is required")
        requireCanAccessGroup(actor, groupId)
        return groupRepository.memberIdsForGroup(groupId).mapNotNull { memberId ->
            val platformAccount = accounts?.findById(memberId)
            if (platformAccount != null &&
                (!platformAccount.active || UserRole.STUDENT !in platformAccount.roles)) {
                return@mapNotNull null
            }
            TutorStudentSummary(
                userId = memberId,
                academicGroupId = groupId,
                displayName = platformAccount?.displayName,
                verifiedPlatformStudent = platformAccount != null,
            )
        }
    }

    suspend fun scopeFor(actor: UserSummary): TutorScopeSummary {
        requireTutor(actor)
        val groups = repository.listForTutor(actor.id)
            .filter { it.active && it.revokedAt == null }
            .mapNotNull { groupRepository.find(it.academicGroupId) }
            .filter { it.active }
            .distinctBy { it.id }
            .map { AcademicGroupSummary(id = it.id, name = it.name, active = true) }
        return TutorScopeSummary(tutorUserId = actor.id, groups = groups)
    }

    suspend fun requireCanAccessGroup(actor: UserSummary, academicGroupId: String) {
        if (isAdmin(actor)) return
        requireTutor(actor)
        val group = groupRepository.find(academicGroupId.trim().uppercase())
        val assignment = group?.takeIf { it.active }?.let {
            repository.findActive(actor.id, it.id)
        }
        if (assignment == null || assignment.revokedAt != null) {
            throw ApiException.Forbidden("Tutor is not assigned to this active academic group")
        }
    }

    private fun TutorAssignmentRecord.toSummary(groupName: String) = TutorAssignmentSummary(
        id = id,
        tutorUserId = tutorUserId,
        academicGroupId = academicGroupId,
        academicGroupName = groupName,
        active = active,
        assignedBy = assignedBy,
        assignedAtEpochSeconds = assignedAt.epochSecond,
        revokedAtEpochSeconds = revokedAt?.epochSecond,
        revokedBy = revokedBy,
    )

    private fun requireTutor(actor: UserSummary) {
        if (UserRole.TUTOR !in actor.roles) throw ApiException.Forbidden("Tutor role is required")
    }

    private fun requireAdmin(actor: UserSummary) {
        if (!isAdmin(actor)) throw ApiException.Forbidden("Academic administration permission is required")
    }

    private fun isAdmin(actor: UserSummary): Boolean =
        UserRole.ADMIN in actor.roles || UserRole.SUPER_ADMIN in actor.roles
}
