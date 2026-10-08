package org.companerodeescuela.api.application

import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.api.devices.DeviceTokenRepository
import org.companerodeescuela.api.devices.InMemoryDeviceTokenRepository
import org.companerodeescuela.api.events.AcademicEventService
import org.companerodeescuela.api.events.InMemoryAcademicEventRepository
import org.companerodeescuela.api.events.MongoAcademicEventRepository
import org.companerodeescuela.api.excuses.ExcuseService
import org.companerodeescuela.api.excuses.InMemoryExcuseRepository
import org.companerodeescuela.api.excuses.MongoExcuseRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentService

data class OperationsFeatureGraph(
    val eventService: AcademicEventService,
    val excuseService: ExcuseService,
    val deviceTokenRepository: DeviceTokenRepository,
)

fun buildOperationsFeatureGraph(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    tutoringService: TutorAssignmentService,
    groupRepository: AcademicGroupRepository? = null,
): OperationsFeatureGraph {
    val eventRepository = when {
        !settings.hasAuthentication -> InMemoryAcademicEventRepository()
        settings.mongo.isConfigured -> MongoAcademicEventRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryAcademicEventRepository()
        else -> error("Academic events require MONGODB_URI outside local development")
    }
    val excuseRepository = when {
        !settings.hasAuthentication -> InMemoryExcuseRepository()
        settings.mongo.isConfigured -> MongoExcuseRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryExcuseRepository()
        else -> error("Excuse requests require MONGODB_URI outside local development")
    }

    return OperationsFeatureGraph(
        eventService = AcademicEventService(repository = eventRepository),
        excuseService = ExcuseService(
            repository = excuseRepository,
            tutoring = tutoringService,
            groupRepository = groupRepository,
        ),
        deviceTokenRepository = InMemoryDeviceTokenRepository(),
    )
}
