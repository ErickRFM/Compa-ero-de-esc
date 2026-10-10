package org.companerodeescuela.api.attendance

import java.time.LocalDate
import org.companerodeescuela.api.academic.NativeAcademicProjection
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.shared.contracts.*

/** Campus identities require an actual native class; labels never map provider identities. */
class TeacherCampusRosterService(
    private val occurrenceResolver:AttendanceOccurrenceResolver,
    private val groups:AcademicGroupRepository,
    private val presence:SchoolPresenceService,
    private val attendance:AttendanceRepository,
    private val nativeProjection:NativeAcademicProjection?=null,
    private val accounts:PlatformAccountRepository?=null,
) {
    suspend fun forTeacher(teacherId:String,occurrenceId:String,date:String,nativeActor:UserSummary?=null):TeacherCampusRosterResponse {
        val actor=nativeActor ?: throw ApiException.DependencyUnavailable("An explicit institutional campus identity adapter is required; native group names do not map identities")
        if(actor.id!=teacherId || UserRole.TEACHER !in actor.roles) throw ApiException.Forbidden("The actual assigned native teacher is required")
        val parsed=runCatching { LocalDate.parse(date) }.getOrElse { throw ApiException.Validation("date must be YYYY-MM-DD") }
        val projection=nativeProjection ?: throw ApiException.DependencyUnavailable("Native academic authority is unavailable")
        val occurrence=projection.teacherOccurrence(actor,occurrenceId,parsed)
        val group=groups.find(occurrence.group.id.value) ?: throw ApiException.Forbidden("Actual class group is unavailable")
        val accountRepository=accounts ?: throw ApiException.DependencyUnavailable("Native identity is unavailable")
        val session=attendance.findSessionByOccurrence(occurrenceId)
        if(session!=null) projection.requireSession(actor,session)
        val records=session?.let { attendance.recordsForSession(it.id).associateBy { record -> record.studentId } }.orEmpty()
        val students=mutableListOf<CampusRosterStudent>()
        for(id in projection.studentIdsForTeacher(actor,occurrence.group.course.id.value)) {
            val student=accountRepository.findById(id) ?: continue
            if(!student.permitsSession || student.accountStatus!=AccountStatus.ACTIVE || student.emailVerifiedAt==null || student.institutionId!=group.institutionId || UserRole.STUDENT !in student.roles) continue
            students+=CampusRosterStudent(id,presence.activeFor(id,student.toUserSummary())?.startedAtEpochSeconds,records[id]?.withCurrentPresencePolicy())
        }
        return TeacherCampusRosterResponse(occurrenceId,group.name,session?.id,students)
    }
}
