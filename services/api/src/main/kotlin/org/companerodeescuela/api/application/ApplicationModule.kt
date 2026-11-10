package org.companerodeescuela.api.application

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
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
import org.companerodeescuela.api.admin.MongoOwnerBootstrapClaims
import org.companerodeescuela.api.admin.OwnerBootstrap
import org.companerodeescuela.api.admin.QaAdminBootstrap
import org.companerodeescuela.api.admin.accountAdministrationRoutes
import org.companerodeescuela.api.attendance.attendanceRoutes
import org.companerodeescuela.api.audit.InMemoryPlatformAuditLog
import org.companerodeescuela.api.audit.MongoPlatformAuditLog
import org.companerodeescuela.api.auth.InMemoryOwnerInvitationRepository
import org.companerodeescuela.api.auth.InMemoryRateLimitStore
import org.companerodeescuela.api.auth.LoginAttemptLimiter
import org.companerodeescuela.api.auth.MongoOwnerInvitationRepository
import org.companerodeescuela.api.auth.MongoRateLimitStore
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
import org.companerodeescuela.api.institutions.institutionOwnerProvisionRoutes
import org.companerodeescuela.api.institutions.institutionRoutes
import org.companerodeescuela.api.integrations.ProviderRegistry
import org.companerodeescuela.api.integrations.matica.maticaProgressRoutes
import org.companerodeescuela.api.integrations.academic.siii.SiiiSectionProjectionFeature
import org.companerodeescuela.api.integrations.push.InMemoryInstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.InstitutionalAttendanceHistoryService
import org.companerodeescuela.api.integrations.push.InstitutionalEventProjectionWorker
import org.companerodeescuela.api.integrations.push.InstitutionalIdentityLinkService
import org.companerodeescuela.api.integrations.push.InstitutionalProjectionIdentityResolver
import org.companerodeescuela.api.integrations.push.InstitutionalPushAuthenticator
import org.companerodeescuela.api.integrations.push.InstitutionalPushService
import org.companerodeescuela.api.integrations.push.InstitutionalSecretProtector
import org.companerodeescuela.api.integrations.push.MongoInstitutionalIdentityLinkRepository
import org.companerodeescuela.api.integrations.push.MongoInstitutionalPushRepository
import org.companerodeescuela.api.integrations.push.SiiiPilotCredentialBootstrap
import org.companerodeescuela.api.integrations.push.institutionalAttendanceHistoryRoutes
import org.companerodeescuela.api.integrations.push.institutionalIdentityLinkRoutes
import org.companerodeescuela.api.integrations.push.institutionalPushRoutes
import org.companerodeescuela.api.invitations.InMemoryInvitationOutbox
import org.companerodeescuela.api.invitations.InMemoryInvitationRepository
import org.companerodeescuela.api.invitations.InvitationDispatchService
import org.companerodeescuela.api.invitations.MongoInvitationOutbox
import org.companerodeescuela.api.invitations.MongoInvitationRepository
import org.companerodeescuela.api.invitations.UnavailableInvitationEmailGateway
import org.companerodeescuela.api.invitations.invitationRoutes
import org.companerodeescuela.api.mail.ResendInvitationEmailGateway
import org.companerodeescuela.api.mail.resendWebhookRoutes
import org.companerodeescuela.api.plugins.configurePlugins
import org.companerodeescuela.api.presence.schoolPresenceRoutes
import org.companerodeescuela.api.representatives.groupRepresentativeRoutes
import org.companerodeescuela.api.security.MfaSecretCipher
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
    // Initial ownership is an explicit MFA-authorized operator command, never a
    // privilege inferred from an email, login form or invitation. This bootstrap
    // is scoped to a dedicated QA or production database, with separate MFA proof.
    val qaOwnerSpec = OwnerBootstrap.parse(System::getenv)
    val prodOwnerSpec = OwnerBootstrap.parseProduction(System::getenv)
    val qaAdminSpec = QaAdminBootstrap.parse(System::getenv)
    require(listOfNotNull(qaOwnerSpec, prodOwnerSpec, qaAdminSpec).size <= 1) {
        "Only one privileged bootstrap command may run per startup"
    }
    qaOwnerSpec?.let { desired ->
        require(settings.mongo.isConfigured && settings.hasAuthentication) {
            "QA owner bootstrap requires persistent authenticated identity"
        }
        runBlocking {
            withTimeout(15_000L) {
                OwnerBootstrap.provision(
                    desired,
                    settings.environment.name.lowercase(),
                    settings.mongo.databaseName,
                    identityGraph.accounts,
                    identityGraph.mfa,
                    MongoOwnerBootstrapClaims(mongoConnection.database()),
                )
            }
        }
    }
    // Production ownership is a distinct, explicitly scoped, MFA-authorized command.
    prodOwnerSpec?.let { desired ->
        require(settings.mongo.isConfigured && settings.hasAuthentication) {
            "Production owner bootstrap requires persistent authenticated identity"
        }
        runBlocking {
            withTimeout(15_000L) {
                OwnerBootstrap.provision(
                    desired,
                    settings.environment.name.lowercase(),
                    settings.mongo.databaseName,
                    identityGraph.accounts,
                    identityGraph.mfa,
                    MongoOwnerBootstrapClaims(mongoConnection.database()),
                )
            }
        }
    }
    // One-time institution-level provisioning is a separate action and blocks startup on mismatch.
    qaAdminSpec?.let { desired ->
        require(
            settings.mongo.isConfigured && settings.hasAuthentication,
        ) { "QA admin bootstrap requires persistent authenticated identity" }
        runBlocking {
            withTimeout(15_000L) {
                org.companerodeescuela.api.admin.QaAdminBootstrap.provision(
                    desired,
                    settings.environment.name.lowercase(),
                    settings.mongo.databaseName,
                    identityGraph.accounts,
                    identityGraph.institutions,
                    identityGraph.mfa,
                    org.companerodeescuela.api.admin
                        .MongoQaAdminBootstrapClaims(mongoConnection.database()),
                )
            }
        }
    }
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
    val institutionalAuthorityKeyOverrides =
        org.companerodeescuela.api.integrations.academic.siii.SiiiHttpAcademicAuthority.keyOverridesFromJson(
            System.getenv("SIII_ACADEMIC_AUTHORITY_ROLLOVER_ENABLED"),
            System.getenv("SIII_ACADEMIC_AUTHORITY_ROLLOVER_KEY_IDS_JSON"),
            institutionalAuthorityTargets,
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
                    rolloverKeyIds = institutionalAuthorityKeyOverrides,
                ),
                institutionalAuthorityTargets.keys,
            )
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
    val attendanceRepository = buildAttendanceRepository(settings, mongoConnection)
    val attendancePushEligibility =
        org.companerodeescuela.api.attendance.AttendancePushEligibility(
            attendanceRepository,
            nativeAcademic,
            identityGraph.accounts,
            presenceGraph?.service,
            institutions = identityGraph.institutions,
            tasks = classroomGraph.channelTaskService,
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
            authorizePushMessage = attendancePushEligibility::allows,
        )
    classroomGraph.channelTaskService.attachNotifications(operationsGraph.pushService)
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
            repository = attendanceRepository,
        )

    val academicChangeNotifier =
        org.companerodeescuela.api.integrations.academic.siii.SiiiAcademicChangeNotifier(
            institutionalIdentityLinkService,
            operationsGraph.pushService,
        )
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
                    commandHandler =
                        org.companerodeescuela.api.integrations.academic.siii.SiiiSectionProjectionHandler(
                            sections.repository,
                            notificationOwners = academicChangeNotifier::owners,
                            notifyChange = academicChangeNotifier::enqueue,
                        ),
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

    launchManagedWorker(enabled = settings.hasAuthentication && settings.mongo.isConfigured, context = Dispatchers.IO) {
        while (kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
            try {
                attendanceGraph.sessionService.reconcileClosures()
            } catch (
                cancelled: kotlinx.coroutines.CancellationException,
            ) {
                throw cancelled
            } catch (_: Exception) {
                log.warn("Attendance closure replay unavailable; durable work retained")
            }
            kotlinx.coroutines.delay(15_000)
        }
    }

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

    val rateLimitStore =
        if (settings.hasAuthentication && settings.mongo.isConfigured) {
            MongoRateLimitStore(mongoConnection.database())
        } else {
            InMemoryRateLimitStore()
        }
    val loginAttemptLimiter =
        LoginAttemptLimiter(
            progressiveDelayBase = java.time.Duration.ofSeconds(settings.loginProgressiveDelayBaseSeconds),
            progressiveDelayMaximum = java.time.Duration.ofSeconds(settings.loginProgressiveDelayMaxSeconds),
            store = rateLimitStore,
        )
    val platformAuditLog =
        if (settings.hasAuthentication && settings.mongo.isConfigured) {
            MongoPlatformAuditLog(mongoConnection.database())
        } else {
            InMemoryPlatformAuditLog()
        }
    val invitationRepository =
        if (settings.hasAuthentication && settings.mongo.isConfigured) {
            MongoInvitationRepository(mongoConnection.database())
        } else {
            InMemoryInvitationRepository()
        }
    val invitationOutbox =
        if (settings.hasAuthentication && settings.mongo.isConfigured && settings.mfaEncryptionKey != null) {
            MongoInvitationOutbox(mongoConnection.database(), MfaSecretCipher(settings.mfaEncryptionKey))
        } else {
            InMemoryInvitationOutbox()
        }
    val invitationEmail =
        if (settings.hasVerificationEmail) {
            ResendInvitationEmailGateway(
                requireNotNull(settings.verificationEmailApiKey),
                requireNotNull(settings.verificationEmailSender),
            )
        } else {
            UnavailableInvitationEmailGateway
        }
    val invitationDispatcher =
        InvitationDispatchService(
            identityGraph.accounts,
            identityGraph.mfa,
            invitationRepository,
            invitationEmail,
            identityGraph.institutions,
            invitationOutbox,
        )
    val invitationWorkerConfigured =
        settings.hasAuthentication &&
            settings.mongo.isConfigured &&
            settings.hasVerificationEmail &&
            settings.mfaEncryptionKey != null
    val invitationWorkerAllowed = !settings.environment.isProduction || settings.productionInvitationsEnabled
    launchManagedWorker(
        enabled = invitationWorkerConfigured && invitationWorkerAllowed,
        context = Dispatchers.IO,
    ) {
        while (kotlin.coroutines.coroutineContext[Job]?.isActive == true) {
            try {
                invitationDispatcher.retryPendingOutbox()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                log.warn("Invitation outbox retry unavailable; encrypted work retained")
            }
            kotlinx.coroutines.delay(30_000)
        }
    }

    val ownerInvitationRepository =
        if (settings.hasAuthentication && settings.mongo.isConfigured) {
            MongoOwnerInvitationRepository(mongoConnection.database())
        } else {
            InMemoryOwnerInvitationRepository()
        }

    routing {
        healthRoutes(settings = settings, healthService = healthService)
        resendWebhookRoutes(
            signingSecret = settings.resendWebhookSecret,
            database =
                if (settings.hasAuthentication && settings.mongo.isConfigured) {
                    mongoConnection.database()
                } else {
                    null
                },
        )
        authRoutes(
            settings = settings,
            identityProvider = providerRegistry.identity,
            loginAttemptLimiter = loginAttemptLimiter,
            sessions = identityGraph.refreshSessions,
            accounts = identityGraph.accounts,
            institutions = identityGraph.institutions,
            verificationEmail = identityGraph.verificationEmail,
            sessionAuthority = sessionAuthority,
            ownerInvitationRepository = ownerInvitationRepository,
        )
        val maticaIntegrationSecret = System.getenv("MATICA_COMPANION_INTEGRATION_SECRET")
        val maticaApiBaseUrl = System.getenv("MATICA_API_BASE_URL")
        maticaProgressRoutes(
            settings = settings,
            accounts = identityGraph.accounts,
            sessions = identityGraph.refreshSessions,
            database = if (settings.mongo.isConfigured && !maticaIntegrationSecret.isNullOrBlank() && !maticaApiBaseUrl.isNullOrBlank()) {
                mongoConnection.database()
            } else {
                null
            },
            sharedSecret = maticaIntegrationSecret,
            apiBaseUrl = maticaApiBaseUrl,
        )
        mfaRoutes(settings, providerRegistry.identity, identityGraph.refreshSessions, identityGraph.accounts, identityGraph.mfa)
        accountAdministrationRoutes(
            settings,
            providerRegistry.identity,
            identityGraph.refreshSessions,
            identityGraph.accounts,
            administrationService,
            auditLog = platformAuditLog,
        )
        institutionalIdentityLinkRoutes(
            settings,
            providerRegistry.identity,
            identityGraph.refreshSessions,
            identityGraph.accounts,
            institutionalIdentityLinkService,
        )
        invitationRoutes(
            settings,
            providerRegistry.identity,
            identityGraph.refreshSessions,
            identityGraph.accounts,
            invitationDispatcher,
            invitationRepository,
            rateLimitStore = rateLimitStore,
            auditLog = platformAuditLog,
        )
        institutionRoutes(identityGraph.institutions)
        institutionOwnerProvisionRoutes(
            settings,
            providerRegistry.identity,
            identityGraph.refreshSessions,
            identityGraph.accounts,
            identityGraph.mfa,
            identityGraph.institutions,
            rateLimitStore = rateLimitStore,
            auditLog = platformAuditLog,
        )
        institutionalPushRoutes(
            institutionalPushAuthenticator,
            institutionalPushService,
            institutionalPushRepository,
            institutionalPushRepository,
        )
        institutionalAttendanceHistoryRoutes(
            institutionalPushAuthenticator,
            InstitutionalAttendanceHistoryService(
                institutionalIdentityLinkService,
                attendanceGraph.studentService,
                nativeAcademic.institutionalAttendance,
                archivalRecords = attendanceRepository,
            ),
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
        channelRoutes(
            settings = settings,
            service = classroomGraph.channelService,
            taskService = classroomGraph.channelTaskService,
        )
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
