package org.companerodeescuela.api.devices

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.requirePlatformPrincipal
import org.companerodeescuela.api.auth.subjectId
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse

@Serializable
data class DeviceRegistrationRequest(
    val token: String,
    val platform: String = "android",
    val model: String? = null,
)

interface DeviceTokenRepository {
    suspend fun register(userId: String, token: String, platform: String, model: String?)
    suspend fun unregister(userId: String, token: String): Boolean
    suspend fun listTokens(userIds: Set<String>): List<String>
}

class InMemoryDeviceTokenRepository : DeviceTokenRepository {
    private val storage = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun register(userId: String, token: String, platform: String, model: String?) {
        storage.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(token)
    }

    override suspend fun unregister(userId: String, token: String): Boolean {
        return storage[userId]?.remove(token) ?: false
    }

    override suspend fun listTokens(userIds: Set<String>): List<String> {
        return userIds.flatMap { storage[it].orEmpty() }
    }
}

fun Route.deviceRoutes(
    settings: ApiSettings,
    repository: DeviceTokenRepository,
) {
    route("/devices") {
        if (!settings.hasAuthentication) return@route

        authenticate(AuthTokenService.PROVIDER_NAME) {
            post("/register") {
                val principal = call.requirePlatformPrincipal()
                val request = call.receive<DeviceRegistrationRequest>()
                repository.register(
                    userId = principal.subjectId(),
                    token = request.token,
                    platform = request.platform,
                    model = request.model,
                )
                call.respond(
                    ApiResponse(
                        data = true,
                        requestId = call.requestId(),
                    ),
                )
            }

            delete("/register") {
                val principal = call.requirePlatformPrincipal()
                val request = call.receive<DeviceRegistrationRequest>()
                val removed = repository.unregister(principal.subjectId(), request.token)
                call.respond(
                    ApiResponse(
                        data = removed,
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}
