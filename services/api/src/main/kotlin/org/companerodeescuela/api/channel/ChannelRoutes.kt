package org.companerodeescuela.api.channel

import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.ChannelAcknowledgementRequest
import org.companerodeescuela.shared.contracts.CreateChannelPostRequest
import org.companerodeescuela.shared.contracts.UpdateChannelPostRequest
import org.companerodeescuela.shared.contracts.UserRole

fun Route.channelRoutes(
    settings: ApiSettings,
    service: ChannelService,
) {
    route("/channels") {
        if (!settings.hasAuthentication) {
            get {
                throw ApiException.DependencyUnavailable("Authentication is not configured")
            }
            return@route
        }

        authenticate(AuthTokenService.PROVIDER_NAME) {
            get {
                val principal = call.requirePrincipal()
                call.respond(
                    ApiResponse(
                        data = service.channelsFor(principal.subject(), principal.roles()),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{channelId}/posts") {
                val principal = call.requirePrincipal()
                val channelId = call.requireChannelId()
                call.respond(
                    ApiResponse(
                        data = service.postsFor(
                            userId = principal.subject(),
                            roles = principal.roles(),
                            channelId = channelId,
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            post("/{channelId}/posts") {
                val principal = call.requirePrincipal()
                val channelId = call.requireChannelId()
                call.respond(
                    ApiResponse(
                        data = service.createPost(
                            authorId = principal.subject(),
                            authorDisplayName = principal.displayName(),
                            roles = principal.roles(),
                            channelId = channelId,
                            request = call.receive<CreateChannelPostRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            patch("/{channelId}/posts/{postId}") {
                val principal = call.requirePrincipal()
                val channelId = call.requireChannelId()
                val postId = call.requirePostId()
                call.respond(
                    ApiResponse(
                        data = service.updatePost(
                            actorId = principal.subject(),
                            roles = principal.roles(),
                            channelId = channelId,
                            postId = postId,
                            request = call.receive<UpdateChannelPostRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            delete("/{channelId}/posts/{postId}") {
                val principal = call.requirePrincipal()
                val channelId = call.requireChannelId()
                val postId = call.requirePostId()
                call.respond(
                    ApiResponse(
                        data = service.deletePost(
                            actorId = principal.subject(),
                            roles = principal.roles(),
                            channelId = channelId,
                            postId = postId,
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            put("/{channelId}/posts/{postId}/acknowledgement") {
                val principal = call.requirePrincipal()
                val channelId = call.requireChannelId()
                val postId = call.requirePostId()
                call.respond(
                    ApiResponse(
                        data = service.acknowledge(
                            studentId = principal.subject(),
                            roles = principal.roles(),
                            channelId = channelId,
                            postId = postId,
                            request = call.receive<ChannelAcknowledgementRequest>(),
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }

            get("/{channelId}/posts/{postId}/stats") {
                val principal = call.requirePrincipal()
                val channelId = call.requireChannelId()
                val postId = call.requirePostId()
                call.respond(
                    ApiResponse(
                        data = service.statsFor(
                            actorId = principal.subject(),
                            roles = principal.roles(),
                            channelId = channelId,
                            postId = postId,
                        ),
                        requestId = call.requestId(),
                    ),
                )
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requirePrincipal(): JWTPrincipal =
    principal<JWTPrincipal>() ?: throw ApiException.Unauthorized()

private fun io.ktor.server.application.ApplicationCall.requireChannelId(): String =
    parameters["channelId"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("channelId is required")

private fun io.ktor.server.application.ApplicationCall.requirePostId(): String =
    parameters["postId"]?.takeIf(String::isNotBlank)
        ?: throw ApiException.Validation("postId is required")

private fun JWTPrincipal.subject(): String =
    payload.subject?.takeIf(String::isNotBlank) ?: throw ApiException.Unauthorized()

private fun JWTPrincipal.displayName(): String =
    payload.getClaim("display_name").asString()?.takeIf(String::isNotBlank)
        ?: "Docente"

private fun JWTPrincipal.roles(): Set<UserRole> =
    payload.getClaim("roles")
        .asList(String::class.java)
        .orEmpty()
        .mapNotNull { encoded -> runCatching { UserRole.valueOf(encoded) }.getOrNull() }
        .toSet()
