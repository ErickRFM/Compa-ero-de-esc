package org.companerodeescuela.api.application

import org.companerodeescuela.api.attendance.AttendanceAccessPolicy
import org.companerodeescuela.api.attendance.AttendanceEnrollmentResolver
import org.companerodeescuela.api.attendance.AttendanceQrService
import org.companerodeescuela.api.attendance.AttendanceReviewService
import org.companerodeescuela.api.attendance.AttendanceSessionService
import org.companerodeescuela.api.attendance.AttendanceStudentService
import org.companerodeescuela.api.attendance.InMemoryAttendanceRepository
import org.companerodeescuela.api.attendance.MongoAttendanceRepository
import org.companerodeescuela.api.attendance.ProviderAttendanceOccurrenceResolver
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.attendance.TeacherCampusRosterService
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.presence.SchoolPresenceService

data class AttendanceFeatureGraph(
    val sessionService: AttendanceSessionService,
    val studentService: AttendanceStudentService,
    val reviewService: AttendanceReviewService,
    val qrService: AttendanceQrService?,
    val campusRoster: TeacherCampusRosterService?,
)

fun buildAttendanceFeatureGraph(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    academicProvider: AcademicProvider,
    schoolPresenceService: SchoolPresenceService?,
    groupRepository: AcademicGroupRepository,
): AttendanceFeatureGraph {
    val repository = when {
        !settings.hasAuthentication -> InMemoryAttendanceRepository()
        settings.mongo.isConfigured -> MongoAttendanceRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryAttendanceRepository()
        else -> error("Attendance requires MONGODB_URI outside local development")
    }
    val qrService = settings.attendanceQrSecret?.let { secret ->
        AttendanceQrService(
            secret = secret,
            repository = repository,
        )
    }
    val accessPolicy = AttendanceAccessPolicy(repository)
    val occurrenceResolver = ProviderAttendanceOccurrenceResolver(academicProvider)
    return AttendanceFeatureGraph(
        sessionService = AttendanceSessionService(
            repository = repository,
            occurrenceResolver = occurrenceResolver,
            accessPolicy = accessPolicy,
        ),
        studentService = AttendanceStudentService(
            repository = repository,
            enrollmentResolver = AttendanceEnrollmentResolver(academicProvider),
            qrService = qrService,
            schoolPresenceService = schoolPresenceService,
        ),
        reviewService = AttendanceReviewService(
            repository = repository,
            accessPolicy = accessPolicy,
        ),
        qrService = qrService,
        campusRoster = schoolPresenceService?.let { presence ->
            TeacherCampusRosterService(occurrenceResolver, groupRepository, presence, repository)
        },
    )
}
