package org.companerodeescuela.api.attendance

import org.companerodeescuela.api.admin.InstitutionScopePolicy
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.AdminCapability
import org.companerodeescuela.shared.contracts.AttendanceIdentitySource
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.UserRole
import org.companerodeescuela.shared.contracts.UserSummary

/** Native authority comes from current account capabilities and the persisted class scope. */
class AttendanceAdministrationPolicy(
    private val accounts: PlatformAccountRepository,
    private val classrooms: ClassroomService,
    private val projection: org.companerodeescuela.api.academic.NativeAcademicProjection? = null,
) {
    private val scopes = InstitutionScopePolicy(accounts)

    suspend fun requireAdministration(actor: UserSummary) {
        val current = scopes.live(actor)
        if (UserRole.SUPER_ADMIN in current.roles) return
        scopes.require(actor, AdminCapability.MANAGE_ACADEMICS, current.institutionId ?: "")
    }

    suspend fun requireResource(
        actor: UserSummary,
        session: AttendanceSessionResponse,
        administrative: Boolean,
    ) {
        if (session.academicSource == SiiiAttendanceAuthority.SOURCE) {
            val current = scopes.live(actor)
            if (administrative) {
                scopes.require(actor, AdminCapability.MANAGE_ACADEMICS, session.institutionId ?: "")
            } else if (current.institutionId != session.institutionId ||
                UserRole.TEACHER !in current.roles ||
                session.openedBy != current.id
            ) {
                throw ApiException.Forbidden("Current assigned teacher is required")
            }
            val teacher = accounts.findById(session.openedBy) ?: throw ApiException.Forbidden("Assigned teacher is unavailable")
            (projection ?: throw ApiException.DependencyUnavailable("Institutional attendance authority is unavailable"))
                .requireSession(teacher.toUserSummary(), session)
            return
        }
        if (session.identitySource != AttendanceIdentitySource.NATIVE ||
            session.institutionId.isNullOrBlank() ||
            session.groupId.isNullOrBlank() ||
            session.groupScopeRevision < 0
        ) {
            throw ApiException.Forbidden("Persisted native attendance source and scope are required")
        }
        if (administrative) {
            requireAdministration(actor)
        } else {
            val current = scopes.live(actor)
            if (UserRole.TEACHER !in current.roles ||
                session.openedBy != current.id
            ) {
                throw ApiException.Forbidden("Current assigned teacher is required")
            }
        }
        val classroom =
            try {
                if (administrative) {
                    classrooms.requireCanRead(actor, session.courseId)
                } else {
                    classrooms.requireCanPublish(actor, session.courseId)
                }
            } catch (_: ApiException.NotFound) {
                throw ApiException.Forbidden("Attendance has no current native classroom scope")
            }
        val group = classrooms.scopedGroupFor(actor, session.courseId)
        if (session.institutionId != group.institutionId ||
            session.groupId != group.id ||
            session.groupScopeRevision != group.scopeRevision
        ) {
            throw ApiException.Forbidden("Attendance group scope is stale or inconsistent")
        }
        if (projection != null) {
            val teacher = accounts.findById(classroom.teacherId) ?: throw ApiException.Forbidden("Actual assigned teacher is unavailable")
            try {
                projection.requireSession(teacher.toUserSummary(), session)
            } catch (
                _: ApiException.Unauthorized,
            ) {
                throw ApiException.Forbidden("Attendance teacher identity is no longer active")
            }
        }
        if (classroom.teacherId != session.openedBy ||
            classroom.groupName != session.groupName
        ) {
            throw ApiException.Forbidden("Attendance does not match its actual classroom")
        }
    }
}
