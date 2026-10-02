package org.companerodeescuela.api.health

import java.time.Clock
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.shared.contracts.DependencyStatus
import org.companerodeescuela.shared.contracts.HealthResponse
import org.companerodeescuela.shared.contracts.ReadinessResponse
import org.companerodeescuela.shared.contracts.ServiceStatus
import org.slf4j.LoggerFactory

/**
 * Builds the payload for `GET /health`.
 *
 * Separated from the routing so the aggregation logic can be unit tested
 * without starting a server.
 */
class HealthService(
    private val settings: ApiSettings,
    private val mongoConnection: MongoConnection,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(HealthService::class.java)

    /**
     * Liveness plus a shallow dependency probe.
     *
     * The process is `DEGRADED` rather than `DOWN` when only the database is
     * unreachable: `/health` answering successfully is what keeps a restart
     * loop from turning a transient database outage into an outage of the whole
     * service. `/ready` is the endpoint that gates traffic.
     */
    suspend fun health(): HealthResponse {
        val dependencies = listOf(checkDatabase())
        val hasDownDependency = dependencies.any { it.status == ServiceStatus.DOWN }
        val status = if (hasDownDependency) ServiceStatus.DEGRADED else ServiceStatus.UP

        return HealthResponse(
            service = settings.serviceName,
            status = status,
            version = settings.version,
            environment = settings.environment.name.lowercase(),
            timestamp = clock.instant(),
            dependencies = dependencies,
        )
    }

    /**
     * Readiness: can this instance serve traffic right now?
     *
     * Returns 200 when the database is reachable and 503 otherwise; the route
     * decides the status code from [ServiceStatus].
     */
    suspend fun readiness(): ReadinessResponse {
        val checks = listOf(checkDatabase())
        val ready = checks.all { it.status == ServiceStatus.UP }

        return ReadinessResponse(
            status = if (ready) ServiceStatus.UP else ServiceStatus.DOWN,
            timestamp = clock.instant(),
            checks = checks,
        )
    }

    private suspend fun checkDatabase(): DependencyStatus {
        if (!settings.mongo.isConfigured) {
            return DependencyStatus(
                name = MONGODB_DEPENDENCY,
                status = ServiceStatus.DEGRADED,
                detail = "not_configured",
            )
        }
        val reachable = runCatching { mongoConnection.ping() }.getOrDefault(false)
        return if (reachable) {
            DependencyStatus(name = MONGODB_DEPENDENCY, status = ServiceStatus.UP)
        } else {
            log.warn("Health check could not reach MongoDB")
            DependencyStatus(
                name = MONGODB_DEPENDENCY,
                status = ServiceStatus.DOWN,
                detail = "unreachable",
            )
        }
    }

    companion object {
        const val MONGODB_DEPENDENCY = "mongodb"
    }
}
