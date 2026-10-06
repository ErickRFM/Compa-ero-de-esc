package org.companerodeescuela.api.application

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.routing
import org.companerodeescuela.api.academic.AcademicScheduleManagementService
import org.companerodeescuela.api.academic.InMemoryAcademicScheduleOverrideRepository
import org.companerodeescuela.api.academic.MongoAcademicScheduleOverrideRepository
import org.companerodeescuela.api.academic.academicRoutes
import org.companerodeescuela.api.attendance.AttendanceQrService
import org.companerodeescuela.api.attendance.AttendanceService
import org.companerodeescuela.api.attendance.InMemoryAttendanceRepository
import org.companerodeescuela.api.attendance.MongoAttendanceRepository
import org.companerodeescuela.api.attendance.attendanceRoutes
import org.companerodeescuela.api.auth.authRoutes
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.MongoPlatformAccountRepository
import org.companerodeescuela.api.auth.MongoRefreshSessionRepository
import org.companerodeescuela.api.channel.ChannelAccessPolicy
import org.companerodeescuela.api.channel.ChannelService
import org.companerodeescuela.api.channel.InMemoryChannelRepository
import org.companerodeescuela.api.channel.MongoChannelRepository
import org.companerodeescuela.api.channel.channelRoutes
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.classroom.MongoClassroomRepository
import org.companerodeescuela.api.classroom.classroomRoutes
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.api.events.AcademicEventService
import org.companerodeescuela.api.events.InMemoryAcademicEventRepository
import org.companerodeescuela.api.events.MongoAcademicEventRepository
import org.companerodeescuela.api.events.academicEventRoutes
import org.companerodeescuela.api.devices.InMemoryDeviceTokenRepository
import org.companerodeescuela.api.devices.deviceRoutes
import org.companerodeescuela.api.health.HealthService
import org.companerodeescuela.api.health.healthRoutes
import org.companerodeescuela.api.integrations.ProviderRegistry
import org.companerodeescuela.api.plugins.configurePlugins

/**
 * Composes the application graph.
 *
 * This is the only place where concrete implementations are chosen. Routes,
 * services and repositories receive their collaborators as parameters, so the
 * whole graph can be rebuilt in tests with fakes and no environment variables.
 *
 * @param providerRegistry selects which institutional providers are active.
 *   It defaults to the mock registry so a developer can boot the API with no
 *   external credentials. Production wiring must pass an explicit registry.
 */
fun Application.module(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    providerRegistry: ProviderRegistry = ProviderRegistry.mocks(),
) {
    ProviderRegistry.requireEnvironmentSatisfied(providerRegistry, settings.environment)

    val healthService = HealthService(settings = settings, mongoConnection = mongoConnection)
    val refreshSessionRepository = when {
        !settings.hasAuthentication -> InMemoryRefreshSessionRepository()
        settings.mongo.isConfigured -> MongoRefreshSessionRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryRefreshSessionRepository()
        else -> error("Refresh sessions require MONGODB_URI outside local development")
    }
    val platformAccountRepository = when {
        !settings.hasAuthentication -> InMemoryPlatformAccountRepository()
        settings.mongo.isConfigured -> MongoPlatformAccountRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryPlatformAccountRepository()
        else -> error("Platform accounts require MONGODB_URI outside local development")
    }
    configurePlugins(settings, refreshSessions = refreshSessionRepository)
    val attendanceRepository = when {
        !settings.hasAuthentication -> InMemoryAttendanceRepository()
        settings.mongo.isConfigured -> MongoAttendanceRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryAttendanceRepository()
        else -> error("Attendance requires MONGODB_URI outside local development")
    }
    val classroomRepository = when {
        !settings.hasAuthentication -> InMemoryClassroomRepository()
        settings.mongo.isConfigured -> MongoClassroomRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryClassroomRepository()
        else -> error("Native classrooms require MONGODB_URI outside local development")
    }
    val classroomService = ClassroomService(classroomRepository)
    val channelRepository = when {
        !settings.hasAuthentication -> InMemoryChannelRepository()
        settings.mongo.isConfigured -> MongoChannelRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryChannelRepository()
        else -> error("Class channels require MONGODB_URI outside local development")
    }
    val attendanceQrService = settings.attendanceQrSecret?.let { secret ->
        AttendanceQrService(
            secret = secret,
            repository = attendanceRepository,
        )
    }
    val attendanceService = AttendanceService(
        repository = attendanceRepository,
        academicProvider = providerRegistry.academic,
        qrService = attendanceQrService,
    )
    val channelService = ChannelService(
        repository = channelRepository,
        accessPolicy = ChannelAccessPolicy(
            academicProvider = providerRegistry.academic,
            classroomService = classroomService,
        ),
    )

    val scheduleOverrideRepository = when {
        !settings.hasAuthentication -> InMemoryAcademicScheduleOverrideRepository()
        settings.mongo.isConfigured ->
            MongoAcademicScheduleOverrideRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryAcademicScheduleOverrideRepository()
        else -> error("Manual academic schedules require MONGODB_URI outside local development")
    }
    val scheduleManagementService = AcademicScheduleManagementService(
        repository = scheduleOverrideRepository,
    )

    val eventRepository = when {
        !settings.hasAuthentication -> InMemoryAcademicEventRepository()
        settings.mongo.isConfigured -> MongoAcademicEventRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryAcademicEventRepository()
        else -> error("Academic events require MONGODB_URI outside local development")
    }
    val eventService = AcademicEventService(repository = eventRepository)
    val deviceTokenRepository = InMemoryDeviceTokenRepository()

    monitor.subscribe(ApplicationStopped) {
        providerRegistry.close()
        mongoConnection.close()
    }

    routing {
        healthRoutes(settings = settings, healthService = healthService)
        authRoutes(
            settings = settings,
            identityProvider = providerRegistry.identity,
            sessions = refreshSessionRepository,
            accounts = platformAccountRepository,
        )
        academicRoutes(
            settings = settings,
            academicProvider = providerRegistry.academic,
            scheduleManagement = scheduleManagementService,
            scheduleOverrides = scheduleOverrideRepository,
        )
        attendanceRoutes(
            settings = settings,
            service = attendanceService,
            qrService = attendanceQrService,
        )
        classroomRoutes(settings = settings, service = classroomService)
        channelRoutes(settings = settings, service = channelService)
        academicEventRoutes(settings = settings, service = eventService)
        deviceRoutes(settings = settings, repository = deviceTokenRepository)
    }
}
