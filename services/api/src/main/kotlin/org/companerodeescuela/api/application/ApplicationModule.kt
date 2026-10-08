package org.companerodeescuela.api.application

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.routing
import org.companerodeescuela.api.academic.groups.academicGroupRoutes
import org.companerodeescuela.api.academic.academicRoutes
import org.companerodeescuela.api.attendance.attendanceRoutes
import org.companerodeescuela.api.auth.authRoutes
import org.companerodeescuela.api.channel.channelRoutes
import org.companerodeescuela.api.classroom.classroomRoutes
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.api.events.academicEventRoutes
import org.companerodeescuela.api.excuses.excuseRoutes
import org.companerodeescuela.api.devices.deviceRoutes
import org.companerodeescuela.api.health.HealthService
import org.companerodeescuela.api.health.healthRoutes
import org.companerodeescuela.api.grading.UnavailableGradeSyncGateway
import org.companerodeescuela.api.grading.gradingRoutes
import org.companerodeescuela.api.integrations.ProviderRegistry
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.presence.schoolPresenceRoutes
import org.companerodeescuela.api.tutoring.tutoringRoutes
import org.companerodeescuela.api.tutoring.tutorCaseRoutes

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
    val identityGraph = buildIdentityFeatureGraph(
        settings = settings,
        mongoConnection = mongoConnection,
    )
    configurePlugins(settings, refreshSessions = identityGraph.refreshSessions)

    val academicGraph = buildAcademicFeatureGraph(
        settings = settings,
        mongoConnection = mongoConnection,
        accounts = identityGraph.accounts,
    )
    val classroomGraph = buildClassroomFeatureGraph(
        settings = settings,
        mongoConnection = mongoConnection,
        academicProvider = providerRegistry.academic,
        groupRepository = academicGraph.groupRepository,
    )
    val presenceGraph = buildPresenceFeatureGraph(
        settings = settings,
        mongoConnection = mongoConnection,
    )
    val attendanceGraph = buildAttendanceFeatureGraph(
        settings = settings,
        mongoConnection = mongoConnection,
        academicProvider = providerRegistry.academic,
        schoolPresenceService = presenceGraph?.service,
    )

    val operationsGraph = buildOperationsFeatureGraph(
        settings = settings,
        mongoConnection = mongoConnection,
        tutoringService = academicGraph.tutoringService,
        groupRepository = academicGraph.groupRepository,
    )

    monitor.subscribe(ApplicationStopped) {
        providerRegistry.close()
        mongoConnection.close()
    }

    routing {
        healthRoutes(settings = settings, healthService = healthService)
        authRoutes(
            settings = settings,
            identityProvider = providerRegistry.identity,
            sessions = identityGraph.refreshSessions,
            accounts = identityGraph.accounts,
        )
        academicRoutes(
            settings = settings,
            academicProvider = providerRegistry.academic,
            scheduleManagement = academicGraph.scheduleManagement,
            scheduleOverrides = academicGraph.scheduleOverrides,
            groupRepository = academicGraph.groupRepository,
        )
        attendanceRoutes(
            settings = settings,
            sessionService = attendanceGraph.sessionService,
            studentService = attendanceGraph.studentService,
            reviewService = attendanceGraph.reviewService,
            qrService = attendanceGraph.qrService,
        )
        presenceGraph?.let { graph ->
            schoolPresenceRoutes(
                settings = settings,
                service = graph.service,
                qrAdminService = graph.qrAdminService,
            )
        }
        academicGroupRoutes(settings = settings, service = academicGraph.groupService)
        tutoringRoutes(settings = settings, service = academicGraph.tutoringService)
        tutorCaseRoutes(settings = settings, service = academicGraph.caseService)
        classroomRoutes(settings = settings, service = classroomGraph.classroomService)
        channelRoutes(settings = settings, service = classroomGraph.channelService)
        academicEventRoutes(settings = settings, service = operationsGraph.eventService)
        excuseRoutes(settings = settings, service = operationsGraph.excuseService)
        deviceRoutes(settings = settings, repository = operationsGraph.deviceTokenRepository)
        gradingRoutes(settings = settings, syncGateway = UnavailableGradeSyncGateway)
    }
}
