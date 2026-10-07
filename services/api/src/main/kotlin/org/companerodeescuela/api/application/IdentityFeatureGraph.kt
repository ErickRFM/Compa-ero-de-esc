package org.companerodeescuela.api.application

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
    )
}
