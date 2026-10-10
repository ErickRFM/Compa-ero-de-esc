package org.companerodeescuela.api.attendance

import org.companerodeescuela.api.admin.InstitutionScopePolicy
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.shared.contracts.*

/** Native authority comes from current account capabilities and the persisted class scope. */
class AttendanceAdministrationPolicy(private val accounts:PlatformAccountRepository,private val classrooms:ClassroomService) {
    private val scopes=InstitutionScopePolicy(accounts)
    suspend fun requireAdministration(actor:UserSummary) {
        val current=scopes.live(actor)
        scopes.require(actor,AdminCapability.MANAGE_ACADEMICS,current.institutionId ?: "")
    }
    suspend fun requireResource(actor:UserSummary,session:AttendanceSessionResponse,administrative:Boolean) {
        if(administrative) requireAdministration(actor)
        else {
            val current=scopes.live(actor)
            if(UserRole.TEACHER !in current.roles || session.openedBy!=current.id) throw ApiException.Forbidden("Current assigned teacher is required")
        }
        val classroom=try {
            if(administrative) classrooms.requireCanRead(actor,session.courseId)
            else classrooms.requireCanPublish(actor,session.courseId)
        } catch (_:ApiException.NotFound) { throw ApiException.Forbidden("Attendance has no current native classroom scope") }
        if(classroom.teacherId!=session.openedBy || classroom.groupName!=session.groupName) throw ApiException.Forbidden("Attendance does not match its actual classroom")
    }
}
