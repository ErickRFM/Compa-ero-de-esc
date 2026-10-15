package org.companerodeescuela.api.application

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.companerodeescuela.api.academic.academicRoutes
import org.companerodeescuela.api.academic.groups.academicGroupRoutes
import org.companerodeescuela.api.admin.AccountAdministrationService
import org.companerodeescuela.api.admin.accountAdministrationRoutes
import org.companerodeescuela.api.attendance.attendanceRoutes
import org.companerodeescuela.api.auth.PlatformSessionAuthority
import org.companerodeescuela.api.auth.authRoutes
import org.companerodeescuela.api.channel.channelRoutes
import org.companerodeescuela.api.classroom.classroomRoutes
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.coordination.coordinationRoutes
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.api.devices.deviceRoutes
import org.companerodeescuela.api.events.academicEventRoutes
import org.companerodeescuela.api.excuses.excuseRoutes
import org.companerodeescuela.api.grading.ClassroomGradeSyncAuthorization
import org.companerodeescuela.api.grading.UnavailableGradeSyncGateway
import org.companerodeescuela.api.grading.gradingRoutes
import org.companerodeescuela.api.health.HealthService
import org.companerodeescuela.api.health.healthRoutes
import org.companerodeescuela.api.institutions.institutionRoutes
import org.companerodeescuela.api.integrations.ProviderRegistry
import org.companerodeescuela.api.integrations.push.InMemoryInstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.api.integrations.push.InstitutionalPushAuthenticator
import org.companerodeescuela.api.integrations.push.InstitutionalPushService
import org.companerodeescuela.api.integrations.push.InstitutionalSecretProtector
import org.companerodeescuela.api.integrations.push.MongoInstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.MongoInstitutionalPushRepository
import org.companerodeescuela.api.integrations.push.institutionalIdentityLinkRoutes
import org.companerodeescuela.api.integrations.push.institutionalPushRoutes
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.presence.schoolPresenceRoutes
import org.companerodeescuela.api.representatives.groupRepresentativeRoutes
import org.companerodeescuela.api.security.mfaRoutes
import org.companerodeescuela.api.tutoring.tutorCaseRoutes
import org.companerodeescuela.api.tutoring.tutoringRoutes

/**
 * Composes the application graph.
 *
 * This is the only place where concrete implementations are chosen. Routes,
 * services and repositories receive their collaborators as parameters, so the
 * whole graph can be rebuilt in tests with fakes and no environment variables.
 *
 * @param providerRegistry selects which institutional providers are active.
 *   It defaults to the environment-scoped mock registry for local development.
 *   Production cannot start until a real registry is explicitly supplied.
 */
fun Application.module(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    providerRegistry: ProviderRegistry = ProviderRegistry.mocks(settings.environment),
) {
    ProviderRegistry.requireEnvironmentSatisfied(providerRegistry, settings.environment)

    val healthService = HealthService(settings = settings, mongoConnection = mongoConnection)
    val identityGraph =
        buildIdentityFeatureGraph(
            settings = settings,
            mongoConnection = mongoConnection,
        )
    val sessionAuthority =
        PlatformSessionAuthority(
            identityGraph.accounts,
            providerRegistry.identity,
            currentUserCacheTtlMillis = settings.currentUserCacheTtlMillis,
        )
    configurePlugins(
        settings,
        refreshSessions = identityGraph.refreshSessions,
        sessionAuthority = sessionAuthority,
    )

    val academicGraph =
        buildAcademicFeatureGraph(
            settings = settings,
            mongoConnection = mongoConnection,
            accounts = identityGraph.accounts,
            mfa = identityGraph.mfa,
            institutions = identityGraph.institutions,
        )
    val institutionalPushRepository =
        if (settings.mongo.isConfigured && settings.institutionIntegrationEncryptionKey != null) {
            MongoInstitutionalPushRepository(
                mongoConnection.database(),
                InstitutionalSecretProtector(settings.institutionIntegrationEncryptionKey),
            )
        } else {
            null
        }
    val institutionalPushAuthenticator =
        institutionalPushRepository?.let { repository ->
            InstitutionalPushAuthenticator(repository, repository)
        }
    val institutionalPushService =
        institutionalPushRepository?.let { repository -> InstitutionalPushService(repository) }
    val administrationService =
        AccountAdministrationService(
            identityGraph.accounts,
            identityGraph.institutions,
            academicGraph.groupRepository,
            academicGraph.tutoringService,
            identityGraph.mfa,
        )
    val institutionalIdentityLinkRepository =
        if (settings.hasAuthentication && settings.mongo.isConfigured) {
            MongoInstitutionalIdentityLinkRepository(mongoConnection.database())
        } else {
            InMemoryInstitutionalIdentityLinkRepository()
        }
    val institutionalIdentityLinkService =
        InstitutionalIdentityLinkService(
            identityGraph.accounts,
            identityGraph.institutions,
            institutionalIdentityLinkRepository,
            identityGraph.mfa,
        )
    val classroomGraph =
        buildClassroomFeatureGraph(
            settings = settings,
            mongoConnection = mongoConnection,
            academicProvider = providerRegistry.academic,
            groupRepository = academicGraph.groupRepository,
            representativeService = academicGraph.representativeService,
            accounts = identityGraph.accounts,
            mfa = identityGraph.mfa,
        )
    val gradingGraph = buildGradingFeatureGraph(settings, mongoConnection, classroomGraph.classroomService)
    val scheduleManagement =
        org.companerodeescuela.api.academic.AcademicScheduleManagementService(
            academicGraph.scheduleOverrides,
            accounts = identityGraph.accounts,
            groups = academicGraph.groupRepository,
            mfa = identityGraph.mfa,
            classrooms = classroomGraph.classroomService,
        )
    val nativeAcademic =
        org.companerodeescuela.api.academic.NativeAcademicProjection(
            identityGraph.accounts,
            classroomGraph.classroomService,
            academicGraph.groupRepository,
            academicGraph.scheduleOverrides,
        )
    val presenceGraph =
        buildPresenceFeatureGraph(
            settings = settings,
            mongoConnection = mongoConnection,
            accounts = identityGraph.accounts,
            mfa = identityGraph.mfa,
            institutions = identityGraph.institutions,
        )
    val attendanceGraph =
        buildAttendanceFeatureGraph(
            settings = settings,
            mongoConnection = mongoConnection,
            academicProvider = providerRegistry.academic,
            schoolPresenceService = presenceGraph?.service,
            groupRepository = academicGraph.groupRepository,
            accounts = identityGraph.accounts,
            classrooms = classroomGraph.classroomService,
            nativeProjection = nativeAcademic,
        )

    val operationsGraph =
        buildOperationsFeatureGraph(
            settings = settings,
            mongoConnection = mongoConnection,
            tutoringService = academicGraph.tutoringService,
            groupRepository = academicGraph.groupRepository,
            accounts = identityGraph.accounts,
            classrooms = classroomGraph.classroomService,
            institutions = identityGraph.institutions,
            mfa = identityGraph.mfa,
            refreshSessions = identityGraph.refreshSessions,
        )

    // Only a fully configured, authenticated Mongo deployment starts the durable worker.
    // A local/test instance never sends live Firebase notifications.
    val pushWorker =
        if (settings.hasFirebasePush && settings.hasAuthentication && settings.mongo.isConfigured) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope ->
                scope.launch { operationsGraph.pushService.runLoop() }
            }
        } else {
            null
        }
    monitor.subscribe(ApplicationStopped) {
        pushWorker?.cancel()
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
            institutions = identityGraph.institutions,
            verificationEmail = identityGraph.verificationEmail,
            sessionAuthority = sessionAuthority,
        )
        mfaRoutes(settings, providerRegistry.identity, identityGraph.refreshSessions, identityGraph.accounts, identityGraph.mfa)
        accountAdministrationRoutes(
            settings,
            providerRegistry.identity,
            identityGraph.refreshSessions,
            identityGraph.accounts,
            administrationService,
        )
        institutionalIdentityLinkRoutes(
            settings,
            providerRegistry.identity,
            identityGraph.refreshSessions,
            identityGraph.accounts,
            institutionalIdentityLinkService,
        )
        institutionRoutes(identityGraph.institutions)
        institutionalPushRoutes(
            institutionalPushAuthenticator,
            institutionalPushService,
            institutionalPushRepository,
        )
        academicRoutes(
            settings = settings,
            academicProvider = providerRegistry.academic,
            scheduleManagement = scheduleManagement,
            scheduleOverrides = academicGraph.scheduleOverrides,
            groupRepository = academicGraph.groupRepository,
            nativeProjection = nativeAcademic,
        )
        attendanceRoutes(
            settings = settings,
            sessionService = attendanceGraph.sessionService,
            studentService = attendanceGraph.studentService,
            reviewService = attendanceGraph.reviewService,
            qrService = attendanceGraph.qrService,
            campusRoster = attendanceGraph.campusRoster,
        )
        presenceGraph?.let { graph ->
            schoolPresenceRoutes(
                settings = settings,
                service = graph.service,
                qrAdminService = graph.qrAdminService,
            )
        }
        academicGroupRoutes(settings = settings, service = academicGraph.groupService)
        coordinationRoutes(settings, academicGraph.groupRepository, identityGraph.accounts)
        tutoringRoutes(settings = settings, service = academicGraph.tutoringService)
        tutorCaseRoutes(settings = settings, service = academicGraph.caseService)
        groupRepresentativeRoutes(settings = settings, service = academicGraph.representativeService)
        classroomRoutes(settings = settings, service = classroomGraph.classroomService)
        channelRoutes(settings = settings, service = classroomGraph.channelService)
        academicEventRoutes(settings = settings, service = operationsGraph.eventService)
        excuseRoutes(settings = settings, service = operationsGraph.excuseService)
        deviceRoutes(settings = settings, repository = operationsGraph.deviceTokenRepository)
        gradingRoutes(
            settings = settings,
            syncGateway = UnavailableGradeSyncGateway,
            syncAuthorization = ClassroomGradeSyncAuthorization(classroomGraph.classroomService),
            gradebookService = gradingGraph.gradebookService,
        )
    }
}
