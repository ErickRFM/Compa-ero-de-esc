package org.companerodeescuela.api.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.shared.contracts.ReadinessResponse
import org.companerodeescuela.shared.contracts.ServiceStatus
import org.companerodeescuela.shared.contracts.VersionResponse

/**
 * Operational endpoints.
 *
 * All three are unauthenticated by design: probes and clients need them before
 * a session exists. None of them may expose configuration or dependency
 * details beyond the coarse status names defined in the contracts.
 */
fun Route.healthRoutes(
    settings: ApiSettings,
    healthService: HealthService,
) {
    get("/health") {
        call.respond(healthService.health())
    }

    get("/ready") {
        val readiness: ReadinessResponse = healthService.readiness()
        val status = if (readiness.status == ServiceStatus.UP) {
            HttpStatusCode.OK
        } else {
            HttpStatusCode.ServiceUnavailable
        }
        call.respond(status = status, message = readiness)
    }

    get("/version") {
        call.respond(
            VersionResponse(
                service = settings.serviceName,
                version = settings.version,
                environment = settings.environment.name.lowercase(),
                apiVersion = settings.apiVersion,
            ),
        )
    }
}
