package org.companerodeescuela.api.application

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import org.companerodeescuela.api.institutions.SiiiPilotInstitutionBootstrap
import org.companerodeescuela.api.institutions.institutionRoutes
import org.companerodeescuela.api.integrations.ProviderRegistry
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionProjectionFeature
import org.companerodeescuela.api.integrations.push.InMemoryInstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.InstitutionalEventProjectionWorker
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionIdentityResolver
import org.companerodeescuela.api.integrations.push.InstitutionalPushAuthenticator
import org.companerodeescuela.api.integrations.push.InstitutionalPushService
import org.companerodeescuela.api.integrations.push.InstitutionalSecretProtector
import org.companerodeescuela.api.integrations.push.MongoInstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.MongoInstitutionalPushRepository
import org.companerodeescuela.api.integrations.push.SiiiPilotCredentialBootstrap
import org.companerodeescuela.api.integrations.push.institutionalIdentityLinkRoutes
import org.companerodeescuela.api.integrations.push.institutionalPushRoutes
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.presence.schoolPresenceRoutes
import org.companerodeescuela.api.representatives.groupRepresentativeRoutes
import org.companerodeescuela.api.security.mfaRoutes
import org.companerodeescuela.api.tutoring.tutorCaseRoutes
import org.companerodeescuela.api.tutoring.tutoringRoutes
import kotlin.coroutines.CoroutineContext

/**
 * Composes the application graph.
 *
 * This is the only place where concrete implementations are chosen. Routes,
 * services and repositories receive their collaborators as parameters, so the
 * whole graph can be rebuilt in tests with fakes and no environment variables.
 *
 * @param providerRegistry selects which institutional providers are active.
 *   It defaults to development mocks locally and unavailable institutional providers in staging/production.
 *   Native Mongo features remain available; real institutional adapters require explicit configuration.
 */
fun Application.module(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    providerRegistry: ProviderRegistry = ProviderRegistry.defaultsFor(settings.environment),
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
    // Explicit pilot metadata bootstrap: creates no accounts or registration access.
    SiiiPilotInstitutionBootstrap.parse(System::getenv)?.let { desired ->
        require(settings.mongo.isConfigured && settings.hasAuthentication) {
            "Institution bootstrap requires configured MongoDB and institutional identity authority"
        }
        runBlocking {
            withTimeout(15_000L) {
                SiiiPilotInstitutionBootstrap.provision(mongoConnection.database(), desired)
            }
        }
    }
    // One-time provisioning is an explicit operator action and blocks startup on mismatch.
    // Do not start an HTTP listener before the encrypted credential is persisted.
    SiiiPilotCredentialBootstrap.parse(System::getenv)?.let { desired ->
        try {
            val repository =
                institutionalPushRepository
                    ?: error("Institutional integration encryption and MongoDB are required for bootstrap")
            runBlocking {
                withTimeout(15_000L) {
                    SiiiPilotCredentialBootstrap.provision(repository, desired) { tenantId ->
                        identityGraph.institutions.findConfigured(tenantId) != null
                    }
                }
            }
        } finally {
            desired.secret.fill(0)
        }
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
    val institutionalSections =
        if (settings.hasAuthentication && institutionalPushRepository != null) {
            SiiiSectionProjectionFeature(
                mongoConnection.database(),
                identityGraph.accounts,
                identityGraph.institutions,
                institutionalIdentityLinkService,
            )
        } else {
            null
        }
    val institutionalAuthorityTargets =
        org.companerodeescuela.api.integrations.academic.siii.SiiiHttpAcademicAuthority.targetsFromJson(
            System.getenv("SIII_ACADEMIC_AUTHORITY_TARGETS_JSON"),
        )
    require(institutionalAuthorityTargets.isEmpty() || institutionalSections != null) {
        "Institutional academic reads require authenticated MongoDB and encrypted integration credentials"
    }
    val institutionalAcademic =
        institutionalSections?.let { sections ->
            org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicService(
                identityGraph.accounts,
                institutionalIdentityLinkRepository,
                sections.reader,
                org.companerodeescuela.api.integrations.academic.siii.SiiiHttpAcademicAuthority(
                    institutionalAuthorityTargets,
                    institutionalPushRepository!!,
                ),
                institutionalAuthorityTargets.keys,
            )
        }
    val institutionalProjectionWorker =
        if (settings.hasAuthentication) {
            institutionalPushRepository?.let { repository ->
                val sections = checkNotNull(institutionalSections)
                InstitutionalEventProjectionWorker(
                    repository = repository,
                    identityResolver =
                        InstitutionalProjectionIdentityResolver { institutionId, reference ->
                            institutionalIdentityLinkService
                                .resolve(
                                    institutionId,
                                    reference.kind,
                                    reference.externalId,
                                )?.id
                        },
                    commandHandler = sections.handler,
                    adapters = listOf(sections.adapter),
                    leaseDurationSeconds = 120,
                )
            }
        } else {
            null
        }
    launchManagedWorker(
        enabled = institutionalProjectionWorker != null,
        context = Dispatchers.IO,
    ) {
        institutionalProjectionWorker?.runForever()
    }
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
            institutionalAttendance =
                institutionalAcademic?.let { academic ->
                    org.companerodeescuela.api.attendance.SiiiAttendanceAuthority(
                        academic,
                        checkNotNull(institutionalSections).reader,
                        institutionalIdentityLinkRepository,
                        institutionalIdentityLinkService,
                        identityGraph.accounts,
                    )
                },
        )
    val presenceGraph =
        buildPresenceFeatureGraph(
            settings = settings,
            mongoConnection = mongoConnection,
            accounts = identityGraph.accounts,
            mfa = identityGraph.mfa,
            institutions = identityGraph.institutions,
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
            pushService = operationsGraph.pushService,
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
            institutionalPushRepository,
        )
        academicRoutes(
            settings = settings,
            academicProvider = providerRegistry.academic,
            scheduleManagement = scheduleManagement,
            scheduleOverrides = academicGraph.scheduleOverrides,
            groupRepository = academicGraph.groupRepository,
            nativeProjection = nativeAcademic,
            institutionalAcademic = institutionalAcademic,
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
        coordinationRoutes(settings, academicGraph.groupRepository, identityGraph.accounts, attendanceGraph.repository)
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

internal fun Application.launchManagedWorker(
    enabled: Boolean,
    context: CoroutineContext = Dispatchers.IO,
    worker: suspend CoroutineScope.() -> Unit,
): Job? {
    if (!enabled) return null
    val scope = CoroutineScope(SupervisorJob() + context)
    val job = scope.launch(block = worker)
    monitor.subscribe(ApplicationStopped) {
        scope.cancel()
    }
    return job
}
