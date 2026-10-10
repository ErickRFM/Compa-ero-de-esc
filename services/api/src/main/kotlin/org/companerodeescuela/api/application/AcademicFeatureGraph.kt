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
import org.companerodeescuela.api.representatives.GroupRepresentativeRepository
import org.companerodeescuela.api.representatives.GroupRepresentativeService
import org.companerodeescuela.api.representatives.InMemoryGroupRepresentativeRepository
import org.companerodeescuela.api.representatives.MongoGroupRepresentativeRepository
import org.companerodeescuela.api.tutoring.InMemoryTutorAssignmentRepository
import org.companerodeescuela.api.tutoring.InMemoryTutorCaseRepository
import org.companerodeescuela.api.tutoring.MongoTutorAssignmentRepository
import org.companerodeescuela.api.tutoring.MongoTutorCaseRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentService
import org.companerodeescuela.api.tutoring.TutorCaseRepository
import org.companerodeescuela.api.tutoring.TutorCaseService

data class AcademicFeatureGraph(
    val groupRepository: AcademicGroupRepository,
    val groupService: AcademicGroupService,
    val tutoringService: TutorAssignmentService,
    val caseService: TutorCaseService,
    val representativeService: GroupRepresentativeService,
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
    val caseRepository: TutorCaseRepository = when {
        !settings.hasAuthentication -> InMemoryTutorCaseRepository()
        settings.mongo.isConfigured -> MongoTutorCaseRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryTutorCaseRepository()
        else -> error("Tutoring cases require MONGODB_URI outside local development")
    }
    val representativeRepository: GroupRepresentativeRepository = when {
        !settings.hasAuthentication -> InMemoryGroupRepresentativeRepository()
        settings.mongo.isConfigured -> MongoGroupRepresentativeRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryGroupRepresentativeRepository()
        else -> error("Group representatives require MONGODB_URI outside local development")
    }
    val scheduleOverrides: AcademicScheduleOverrideRepository = when {
        !settings.hasAuthentication -> InMemoryAcademicScheduleOverrideRepository()
        settings.mongo.isConfigured ->
            MongoAcademicScheduleOverrideRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryAcademicScheduleOverrideRepository()
        else -> error("Manual academic schedules require MONGODB_URI outside local development")
    }

    val tutoringService = TutorAssignmentService(
        repository = tutorRepository,
        groupRepository = groupRepository,
        accounts = accounts,
    )
    val representativeService = GroupRepresentativeService(
        repository = representativeRepository,
        tutorAssignmentService = tutoringService,
    )
    return AcademicFeatureGraph(
        groupRepository = groupRepository,
        groupService = AcademicGroupService(groupRepository),
        tutoringService = tutoringService,
        caseService = TutorCaseService(caseRepository, tutoringService, groupRepository),
        representativeService = representativeService,
        scheduleOverrides = scheduleOverrides,
        scheduleManagement = AcademicScheduleManagementService(scheduleOverrides),
    )
}
