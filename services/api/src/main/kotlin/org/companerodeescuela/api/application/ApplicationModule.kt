package org.companerodeescuela.api.application

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.routing
import org.companerodeescuela.api.academic.academicRoutes
import org.companerodeescuela.api.attendance.AttendanceQrService
import org.companerodeescuela.api.attendance.AttendanceService
import org.companerodeescuela.api.attendance.InMemoryAttendanceRepository
import org.companerodeescuela.api.attendance.MongoAttendanceRepository
import org.companerodeescuela.api.attendance.attendanceRoutes
import org.companerodeescuela.api.auth.authRoutes
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.MongoRefreshSessionRepository
import org.companerodeescuela.api.channel.ChannelAccessPolicy
import org.companerodeescuela.api.channel.ChannelService
import org.companerodeescuela.api.channel.InMemoryChannelRepository
import org.companerodeescuela.api.channel.MongoChannelRepository
import org.companerodeescuela.api.channel.channelRoutes
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
import org.companerodeescuela.api.presence.InMemorySchoolPresenceRepository
import org.companerodeescuela.api.presence.MongoSchoolPresenceRepository
import org.companerodeescuela.api.presence.SchoolPresencePolicy
import org.companerodeescuela.api.presence.SchoolPresenceService
import org.companerodeescuela.api.presence.schoolPresenceRoutes

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
    configurePlugins(settings, refreshSessions = refreshSessionRepository)
    val attendanceRepository = when {
        !settings.hasAuthentication -> InMemoryAttendanceRepository()
        settings.mongo.isConfigured -> MongoAttendanceRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryAttendanceRepository()
        else -> error("Attendance requires MONGODB_URI outside local development")
    }
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
    val schoolPresenceRepository = when {
        settings.mongo.isConfigured -> MongoSchoolPresenceRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemorySchoolPresenceRepository()
        else -> null
    }
    val schoolPresenceService = if (
        settings.hasSchoolPresenceVerification && schoolPresenceRepository != null
    ) {
        SchoolPresenceService(
            repository = schoolPresenceRepository,
            policy = SchoolPresencePolicy(
                entryQrSha256 = settings.schoolPresenceQrSha256,
                allowedSsids = settings.schoolWifiSsids,
                allowedBssids = settings.schoolWifiBssids,
            ),
        )
    } else {
        null
    }
    val attendanceService = AttendanceService(
        repository = attendanceRepository,
        academicProvider = providerRegistry.academic,
        qrService = attendanceQrService,
        schoolPresenceService = schoolPresenceService,
    )
    val channelService = ChannelService(
        repository = channelRepository,
        accessPolicy = ChannelAccessPolicy(providerRegistry.academic),
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
        )
        academicRoutes(settings = settings, academicProvider = providerRegistry.academic)
        attendanceRoutes(
            settings = settings,
            service = attendanceService,
            qrService = attendanceQrService,
        )
        schoolPresenceService?.let { service ->
            schoolPresenceRoutes(settings = settings, service = service)
        }
        channelRoutes(settings = settings, service = channelService)
        academicEventRoutes(settings = settings, service = eventService)
        deviceRoutes(settings = settings, repository = deviceTokenRepository)
    }
}
