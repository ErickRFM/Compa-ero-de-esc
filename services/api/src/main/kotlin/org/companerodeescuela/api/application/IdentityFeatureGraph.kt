package org.companerodeescuela.api.application

import org.companerodeescuela.api.institutions.InstitutionRepository
import org.companerodeescuela.api.institutions.InMemoryInstitutionRepository
import org.companerodeescuela.api.institutions.MongoInstitutionRepository
import org.companerodeescuela.api.mail.VerificationEmailGateway
import org.companerodeescuela.api.mail.UnavailableVerificationEmailGateway
import org.companerodeescuela.api.mail.ResendVerificationEmailGateway
import org.companerodeescuela.api.auth.InMemoryPlatformAccountRepository
import org.companerodeescuela.api.auth.InMemoryRefreshSessionRepository
import org.companerodeescuela.api.auth.MongoPlatformAccountRepository
import org.companerodeescuela.api.auth.MongoRefreshSessionRepository
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.RefreshSessionRepository
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.database.MongoConnection

data class IdentityFeatureGraph(
    val refreshSessions: RefreshSessionRepository,
    val accounts: PlatformAccountRepository,
    val institutions: InstitutionRepository = InMemoryInstitutionRepository(),
    val verificationEmail: VerificationEmailGateway = UnavailableVerificationEmailGateway,
)

fun buildIdentityFeatureGraph(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
): IdentityFeatureGraph {
    val refreshSessions = when {
        !settings.hasAuthentication -> InMemoryRefreshSessionRepository()
        settings.mongo.isConfigured -> MongoRefreshSessionRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryRefreshSessionRepository()
        else -> error("Refresh sessions require MONGODB_URI outside local development")
    }
    val accounts = when {
        !settings.hasAuthentication -> InMemoryPlatformAccountRepository()
        settings.mongo.isConfigured -> MongoPlatformAccountRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryPlatformAccountRepository()
        else -> error("Platform accounts require MONGODB_URI outside local development")
    }
    return IdentityFeatureGraph(
        refreshSessions = refreshSessions,
        accounts = accounts,
        institutions = if (settings.hasAuthentication && settings.mongo.isConfigured) MongoInstitutionRepository(mongoConnection.database()) else InMemoryInstitutionRepository(),
        verificationEmail = if (settings.hasVerificationEmail)
            ResendVerificationEmailGateway(requireNotNull(settings.verificationEmailApiKey), requireNotNull(settings.verificationEmailSender))
            else UnavailableVerificationEmailGateway,
    )
}
