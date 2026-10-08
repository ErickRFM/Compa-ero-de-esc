package org.companerodeescuela.api.application

import org.companerodeescuela.api.academic.AcademicScheduleManagementService
import org.companerodeescuela.api.academic.AcademicScheduleOverrideRepository
import org.companerodeescuela.api.academic.InMemoryAcademicScheduleOverrideRepository
import org.companerodeescuela.api.academic.MongoAcademicScheduleOverrideRepository
import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.academic.groups.AcademicGroupService
import org.companerodeescuela.api.academic.groups.InMemoryAcademicGroupRepository
import org.companerodeescuela.api.academic.groups.MongoAcademicGroupRepository
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.api.tutoring.InMemoryTutorAssignmentRepository
import org.companerodeescuela.api.tutoring.MongoTutorAssignmentRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentService

data class AcademicFeatureGraph(
    val groupRepository: AcademicGroupRepository,
    val groupService: AcademicGroupService,
    val tutoringService: TutorAssignmentService,
    val scheduleOverrides: AcademicScheduleOverrideRepository,
    val scheduleManagement: AcademicScheduleManagementService,
)

fun buildAcademicFeatureGraph(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    accounts: PlatformAccountRepository? = null,
): AcademicFeatureGraph {
    val groupRepository = when {
        !settings.hasAuthentication -> InMemoryAcademicGroupRepository()
        settings.mongo.isConfigured -> MongoAcademicGroupRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryAcademicGroupRepository()
        else -> error("Academic groups require MONGODB_URI outside local development")
    }
    val tutorRepository: TutorAssignmentRepository = when {
        !settings.hasAuthentication -> InMemoryTutorAssignmentRepository()
        settings.mongo.isConfigured -> MongoTutorAssignmentRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryTutorAssignmentRepository()
        else -> error("Tutor assignments require MONGODB_URI outside local development")
    }
    val scheduleOverrides: AcademicScheduleOverrideRepository = when {
        !settings.hasAuthentication -> InMemoryAcademicScheduleOverrideRepository()
        settings.mongo.isConfigured ->
            MongoAcademicScheduleOverrideRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryAcademicScheduleOverrideRepository()
        else -> error("Manual academic schedules require MONGODB_URI outside local development")
    }

    return AcademicFeatureGraph(
        groupRepository = groupRepository,
        groupService = AcademicGroupService(groupRepository),
        tutoringService = TutorAssignmentService(
            repository = tutorRepository,
            groupRepository = groupRepository,
            accounts = accounts,
        ),
        scheduleOverrides = scheduleOverrides,
        scheduleManagement = AcademicScheduleManagementService(scheduleOverrides),
    )
}
