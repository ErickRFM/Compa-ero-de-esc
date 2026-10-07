package org.companerodeescuela.api.application

import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.api.presence.InMemorySchoolEntryQrRepository
import org.companerodeescuela.api.presence.InMemorySchoolPresenceRepository
import org.companerodeescuela.api.presence.MongoSchoolEntryQrRepository
import org.companerodeescuela.api.presence.MongoSchoolPresenceRepository
import org.companerodeescuela.api.presence.SchoolEntryQrService
import org.companerodeescuela.api.presence.SchoolEntryQrVerifier
import org.companerodeescuela.api.presence.SchoolNetworkVerifier
import org.companerodeescuela.api.presence.SchoolPresencePolicy
import org.companerodeescuela.api.presence.SchoolPresenceService

data class PresenceFeatureGraph(
    val service: SchoolPresenceService,
    val qrAdminService: SchoolEntryQrService,
)

fun buildPresenceFeatureGraph(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
): PresenceFeatureGraph? {
    if (!settings.hasSchoolPresenceVerification) return null

    val presenceRepository = when {
        settings.mongo.isConfigured -> MongoSchoolPresenceRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemorySchoolPresenceRepository()
        else -> error("School presence requires MONGODB_URI outside local development")
    }
    val entryQrRepository = when {
        settings.mongo.isConfigured -> MongoSchoolEntryQrRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemorySchoolEntryQrRepository()
        else -> error("School entry QR management requires MONGODB_URI outside local development")
    }

    val entryQrService = SchoolEntryQrService(entryQrRepository)
    val policy = SchoolPresencePolicy(
        entryQrSha256 = settings.schoolPresenceQrSha256,
        allowedSsids = settings.schoolWifiSsids,
        allowedBssids = settings.schoolWifiBssids,
    )
    val service = SchoolPresenceService(
        repository = presenceRepository,
        policy = policy,
        qrVerifier = SchoolEntryQrVerifier(
            managedQrService = entryQrService,
            legacyQrSha256 = policy.entryQrSha256,
        ),
        networkVerifier = SchoolNetworkVerifier(
            allowedSsids = policy.allowedSsids,
            allowedBssids = policy.allowedBssids,
        ),
    )
    return PresenceFeatureGraph(
        service = service,
        qrAdminService = entryQrService,
    )
}
