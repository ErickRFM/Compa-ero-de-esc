package org.companerodeescuela.api.devices

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.SessionIdentitySource
import org.companerodeescuela.api.auth.boundedBody
import org.companerodeescuela.api.auth.requirePlatformPrincipal
import org.companerodeescuela.api.auth.subjectId
import org.companerodeescuela.api.auth.verifiedSessionSource
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse

@Serializable
data class DeviceRegistrationRequest(
    val token: String,
    val platform: String = "android",
    val model: String? = null,
    /** Stable random installation ID; old clients without one remain supported. */
    val deviceId: String? = null,
    val appDistribution: String? = null,
    val packageName: String? = null,
)

interface DeviceTokenRepository {
    suspend fun register(
        userId: String,
        token: String,
        platform: String,
        model: String?,
        deviceId: String? = null,
        sessionId: String? = null,
    )

    suspend fun unregister(
        userId: String,
        token: String,
    ): Boolean

    suspend fun listTokens(userIds: Set<String>): List<String>

    /** A legacy record without a session binding must never be sent push. */
    suspend fun sessionIdFor(
        userId: String,
        token: String,
    ): String? = null

    /** Revoke a token after FCM confirms an UNREGISTERED/invalid destination. */
    suspend fun deactivateInvalid(token: String): Boolean = false
}

/** The sender must use this namespaced owner key, never a raw JWT subject ID. */
internal fun ApplicationCall.deviceOwnerId(): String {
    val source = verifiedSessionSource()
    if (source !in setOf(SessionIdentitySource.NATIVE, SessionIdentitySource.INSTITUTIONAL)) {
        throw ApiException.Forbidden("A current scoped identity is required")
    }
    return "${source.name}:${requirePlatformPrincipal().subjectId()}"
}

fun Route.deviceRoutes(
    settings: ApiSettings,
    repository: DeviceTokenRepository,
) {
    route("/devices") {
        if (!settings.hasAuthentication) return@route
        authenticate(AuthTokenService.PROVIDER_NAME) {
            post("/register") {
                val ownerId = call.deviceOwnerId()
                val request = call.boundedBody(DeviceRegistrationRequest.serializer())
                val sessionId =
                    call
                        .requirePlatformPrincipal()
                        .payload
                        .getClaim("session_id")
                        .asString()
                        ?.takeIf(String::isNotBlank) ?: throw ApiException.Unauthorized()
                repository.register(ownerId, request.token, request.platform, request.model, request.deviceId, sessionId)
                call.respond(ApiResponse(data = true, requestId = call.requestId()))
            }
            delete("/register") {
                val ownerId = call.deviceOwnerId()
                val request = call.boundedBody(DeviceRegistrationRequest.serializer())
                val removed = repository.unregister(ownerId, request.token)
                call.respond(ApiResponse(data = removed, requestId = call.requestId()))
            }
        }
    }
}
