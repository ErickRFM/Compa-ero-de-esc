package org.companerodeescuela.api.application

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.routing
import org.companerodeescuela.api.academic.academicRoutes
import org.companerodeescuela.api.auth.authRoutes
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.database.MongoConnection
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
    // Fails the boot rather than serving fabricated academic data to real users.
    ProviderRegistry.requireEnvironmentSatisfied(providerRegistry, settings.environment)

    configurePlugins(settings)

    val healthService = HealthService(settings = settings, mongoConnection = mongoConnection)

    monitor.subscribe(ApplicationStopped) {
        providerRegistry.close()
        mongoConnection.close()
    }

    routing {
        healthRoutes(settings = settings, healthService = healthService)
        authRoutes(settings = settings, identityProvider = providerRegistry.identity)
        academicRoutes(settings = settings, academicProvider = providerRegistry.academic)
    }
}
