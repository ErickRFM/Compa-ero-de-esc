package org.companerodeescuela.api.representatives

import java.time.Clock
import java.util.UUID
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.tutoring.TutorAssignmentService
import org.companerodeescuela.shared.contracts.AppointRepresentativeRequest
import org.companerodeescuela.shared.contracts.GroupRepresentativeAssignment
import org.companerodeescuela.shared.contracts.GroupRepresentativesOverview
import org.companerodeescuela.shared.contracts.RepresentativePosition
import org.companerodeescuela.shared.contracts.RepresentativeStatus
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

class GroupRepresentativeService(
    private val repository: GroupRepresentativeRepository,
    private val tutorAssignmentService: TutorAssignmentService,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun appointRepresentative(
        actor: UserSummary,
        groupId: String,
        groupName: String,
        request: AppointRepresentativeRequest,
    ): GroupRepresentativeAssignment {
        requireAuthorizedTutorOrAdmin(actor, groupId)

        if (actor.id == request.studentUserId) {
            throw ApiException.Validation("Self-appointment is not allowed")
        }

        val (group,student)=tutorAssignmentService.requireRepresentativeCandidate(groupId,request.studentUserId)
        if(request.academicTermId!=null && request.academicTermId!=group.periodId) throw ApiException.Validation("Representative period must match the actual group")
        val currentActive = repository.findActivePosition(groupId, request.position)
        if (currentActive != null && currentActive.groupScopeRevision==group.scopeRevision &&
            tutorAssignmentService.currentRepresentative(currentActive)) {
            throw ApiException.Conflict("An active or pending ${request.position} already exists for this group")
        }

        // Check if student already holds the other position in this group
        val otherPosition = if (request.position == RepresentativePosition.CHIEF) RepresentativePosition.DEPUTY else RepresentativePosition.CHIEF
        val studentOtherPosition = repository.findActivePosition(groupId, otherPosition)
        if (studentOtherPosition?.studentUserId == request.studentUserId &&
            studentOtherPosition.groupScopeRevision==group.scopeRevision &&
            tutorAssignmentService.currentRepresentative(studentOtherPosition)) {
            throw ApiException.Conflict("Student already holds the other position in this group")
        }

        val now = clock.instant().epochSecond
        val assignment = GroupRepresentativeAssignment(
            id = "REP-" + UUID.randomUUID().toString().take(8),
            academicGroupId = groupId,
            academicGroupName = group.name,
            institutionId=group.institutionId,groupScopeRevision=group.scopeRevision,expiresAtEpochSeconds=group.endsAt?.epochSecond,
            academicTermId = group.periodId,
            studentUserId = request.studentUserId,
            studentDisplayName = student.displayName,
            position = request.position,
            status = RepresentativeStatus.PENDING,
            appointedBy = actor.id,
            appointedAtEpochSeconds = now,
            version = 1L,
        )
        return repository.save(assignment)
    }

    suspend fun acceptAppointment(
        studentUserId: String,
        assignmentId: String,
    ): GroupRepresentativeAssignment {
        val assignment = repository.findById(assignmentId)
            ?: throw ApiException.NotFound("Appointment not found")
        if (assignment.studentUserId != studentUserId) {
            throw ApiException.Forbidden("This appointment does not belong to you")
        }
        if (assignment.status != RepresentativeStatus.PENDING) {
            throw ApiException.Validation("Appointment is no longer pending")
        }

        if(!tutorAssignmentService.currentRepresentative(assignment)) throw ApiException.Forbidden("Current verified student and appointment scope are required")
        val updated = assignment.copy(
            status = RepresentativeStatus.ACTIVE,
            acceptedAtEpochSeconds = clock.instant().epochSecond,
            version = assignment.version + 1,
        )
        return repository.update(updated)
            ?: throw ApiException.Conflict("Concurrent modification of appointment")
    }

    suspend fun declineAppointment(
        studentUserId: String,
        assignmentId: String,
    ): GroupRepresentativeAssignment {
        val assignment = repository.findById(assignmentId)
            ?: throw ApiException.NotFound("Appointment not found")
        if (assignment.studentUserId != studentUserId) {
            throw ApiException.Forbidden("This appointment does not belong to you")
        }
        if (assignment.status != RepresentativeStatus.PENDING) {
            throw ApiException.Validation("Appointment is no longer pending")
        }

        val updated = assignment.copy(
            status = RepresentativeStatus.DECLINED,
            version = assignment.version + 1,
        )
        return repository.update(updated)
            ?: throw ApiException.Conflict("Concurrent modification of appointment")
    }

    suspend fun revokeAppointment(
        actor: UserSummary,
        assignmentId: String,
    ): GroupRepresentativeAssignment {
        val assignment = repository.findById(assignmentId)
            ?: throw ApiException.NotFound("Appointment not found")

        requireAuthorizedTutorOrAdmin(actor, assignment.academicGroupId)

        if (assignment.status != RepresentativeStatus.ACTIVE && assignment.status != RepresentativeStatus.PENDING) {
            throw ApiException.Validation("Appointment is already inactive")
        }

        val updated = assignment.copy(
            status = RepresentativeStatus.REVOKED,
            revokedAtEpochSeconds = clock.instant().epochSecond,
            revokedBy = actor.id,
            version = assignment.version + 1,
        )
        return repository.update(updated)
            ?: throw ApiException.Conflict("Concurrent modification of appointment")
    }

    suspend fun getGroupOverview(
        actor: UserSummary,
        groupId: String,
        groupName: String,
    ): GroupRepresentativesOverview {
        val includeAllPending=tutorAssignmentService.requireRepresentativeRead(actor,groupId)
        val list = repository.listByGroup(groupId).filter { tutorAssignmentService.currentRepresentative(it) }
        val activeChief = list.firstOrNull { it.position == RepresentativePosition.CHIEF && it.status == RepresentativeStatus.ACTIVE }
        val activeDeputy = list.firstOrNull { it.position == RepresentativePosition.DEPUTY && it.status == RepresentativeStatus.ACTIVE }
        val pending = list.filter { it.status == RepresentativeStatus.PENDING && (includeAllPending || it.studentUserId==actor.id) }

        return GroupRepresentativesOverview(
            groupId = groupId,
            groupName = groupName,
            chief = activeChief,
            deputy = activeDeputy,
            pendingInvitations = pending,
        )
    }

    suspend fun getStudentAssignments(studentUserId: String): List<GroupRepresentativeAssignment> {
        return repository.listByStudent(studentUserId)
    }

    suspend fun activeAssignmentsForStudent(studentUserId: String): List<GroupRepresentativeAssignment> {
        return repository.listByStudent(studentUserId).filter { it.status == RepresentativeStatus.ACTIVE && tutorAssignmentService.currentRepresentative(it) }
    }

    private suspend fun requireAuthorizedTutorOrAdmin(actor:UserSummary,groupId:String) {
        if(actor.roles.any { it==UserRole.ADMIN || it==UserRole.SUPER_ADMIN }) tutorAssignmentService.requireCanAccessGroup(actor,groupId)
        else tutorAssignmentService.requireTutorAssignmentForGroup(actor,groupId)
    }
}
