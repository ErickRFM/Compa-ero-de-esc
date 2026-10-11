package org.companerodeescuela.api.application

import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.api.devices.DeviceTokenRepository
import org.companerodeescuela.api.devices.InMemoryDeviceTokenRepository
import org.companerodeescuela.api.devices.MongoDeviceTokenRepository
import org.companerodeescuela.api.devices.MongoPushOutboxRepository
import org.companerodeescuela.api.devices.InMemoryPushOutboxRepository
import org.companerodeescuela.api.devices.FcmHttpV1Gateway
import org.companerodeescuela.api.devices.FirebaseServiceAccount
import org.companerodeescuela.api.devices.PushOutboxService
import org.companerodeescuela.api.devices.UnavailablePushGateway
import org.companerodeescuela.api.events.AcademicEventService
import org.companerodeescuela.api.events.InMemoryAcademicEventRepository
import org.companerodeescuela.api.events.MongoAcademicEventRepository
import org.companerodeescuela.api.excuses.ExcuseService
import org.companerodeescuela.api.excuses.InMemoryExcuseRepository
import org.companerodeescuela.api.excuses.MongoExcuseRepository
import org.companerodeescuela.api.tutoring.TutorAssignmentService
import org.companerodeescuela.shared.contracts.UserRole

data class OperationsFeatureGraph(
    val eventService: AcademicEventService,
    val excuseService: ExcuseService,
    val deviceTokenRepository: DeviceTokenRepository,
    val pushService: PushOutboxService,
)

fun buildOperationsFeatureGraph(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    tutoringService: TutorAssignmentService,
    groupRepository: AcademicGroupRepository? = null,
    accounts:org.companerodeescuela.api.auth.PlatformAccountRepository?=null,
    classrooms:org.companerodeescuela.api.classroom.ClassroomService?=null,
    institutions:org.companerodeescuela.api.institutions.InstitutionRepository?=null,
    mfa:org.companerodeescuela.api.security.MfaService?=null,
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

    val devices: DeviceTokenRepository = when {
        !settings.hasAuthentication -> InMemoryDeviceTokenRepository()
        settings.mongo.isConfigured -> MongoDeviceTokenRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryDeviceTokenRepository()
        else -> error("FCM device registration requires MongoDB outside local development")
    }
    val outbox = when {
        !settings.hasAuthentication -> InMemoryPushOutboxRepository()
        settings.mongo.isConfigured -> MongoPushOutboxRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryPushOutboxRepository()
        else -> error("Push outbox requires MongoDB outside local development")
    }
    val pushSender = settings.firebaseServiceAccountJson?.let {
        FcmHttpV1Gateway(FirebaseServiceAccount.parse(it.concatToString()))
    } ?: UnavailablePushGateway
    val pushService = PushOutboxService(
        devices = devices,
        outbox = outbox,
        sender = pushSender,
        authorizeRecipient = { owner ->
            val id = owner.takeIf { it.startsWith("NATIVE:") }?.removePrefix("NATIVE:")
            val current = id?.let { accounts?.findById(it) }
            current?.permitsSession == true && current.emailVerifiedAt != null &&
                UserRole.STUDENT in current.roles
        },
    )

    return OperationsFeatureGraph(
        eventService = AcademicEventService(repository=eventRepository,accounts=accounts,groups=groupRepository,classrooms=classrooms,institutions=institutions,mfa=mfa,notifications=pushService),
        excuseService = ExcuseService(
            repository = excuseRepository,
            tutoring = tutoringService,
            groupRepository = groupRepository,
        ),
        deviceTokenRepository = devices,
        pushService = pushService,
    )
}
