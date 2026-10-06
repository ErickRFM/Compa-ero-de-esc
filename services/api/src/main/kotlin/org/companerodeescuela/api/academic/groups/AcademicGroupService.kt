package org.companerodeescuela.api.academic.groups

import java.time.Clock
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AcademicGroupMembership
import org.companerodeescuela.shared.contracts.AcademicGroupSummary
import org.companerodeescuela.shared.contracts.AssignAcademicGroupMemberRequest
import org.companerodeescuela.shared.contracts.CreateAcademicGroupRequest
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class AcademicGroupService(
    private val repository: AcademicGroupRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun listFor(actor: UserSummary): List<AcademicGroupSummary> =
        if (isAdmin(actor)) {
            repository.list().map { it.toSummary() }
        } else {
            repository.listGroupsForUser(actor.id).map { it.toSummary() }
        }

    suspend fun create(
        actor: UserSummary,
        request: CreateAcademicGroupRequest,
    ): AcademicGroupSummary {
        requireAdmin(actor)
        val id = normalizeId(request.id)
        val name = request.name.trim().takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("group name is required")
        if (repository.find(id) != null) {
            throw ApiException.Conflict("Academic group already exists")
        }
        val group = AcademicGroupRecord(
            id = id,
            name = name.take(120),
            active = true,
            createdAt = clock.instant(),
        )
        repository.create(group)
        return group.toSummary()
    }

    suspend fun assignMember(
        actor: UserSummary,
        groupId: String,
        request: AssignAcademicGroupMemberRequest,
    ): AcademicGroupMembership {
        requireAdmin(actor)
        val group = repository.find(normalizeId(groupId))
            ?: throw ApiException.NotFound("Academic group was not found")
        val userId = request.userId.trim().takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("userId is required")
        val membership = repository.assign(
            AcademicGroupMembershipRecord(
                groupId = group.id,
                userId = userId,
                joinedAt = clock.instant(),
            ),
        )
        return AcademicGroupMembership(
            groupId = group.id,
            groupName = group.name,
            userId = membership.userId,
            joinedAtEpochSeconds = membership.joinedAt.epochSecond,
        )
    }

    private fun normalizeId(raw: String): String =
        raw.trim().uppercase().replace(Regex("\\s+"), "-")
            .takeIf(String::isNotBlank)
            ?: throw ApiException.Validation("group id is required")

    private fun requireAdmin(actor: UserSummary) {
        if (!isAdmin(actor)) {
            throw ApiException.Forbidden("Academic administration permission is required")
        }
    }

    private fun isAdmin(actor: UserSummary): Boolean =
        UserRole.ADMIN in actor.roles || UserRole.SUPER_ADMIN in actor.roles

    private fun AcademicGroupRecord.toSummary(): AcademicGroupSummary =
        AcademicGroupSummary(id = id, name = name, active = active)
}
