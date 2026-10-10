package org.companerodeescuela.api.events

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.header
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.requireNativeActor
import org.companerodeescuela.api.auth.boundedAuthRequest
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.requestId
import org.companerodeescuela.shared.contracts.*

fun Route.academicEventRoutes(settings:ApiSettings,service:AcademicEventService) {
    val tokens=if(settings.hasAuthentication) AuthTokenService(settings) else null
    route("/events") {
        if(tokens==null) {
            get { throw ApiException.DependencyUnavailable("Authentication is not configured") }
            return@route
        }
        suspend fun ApplicationCall.actor():UserSummary {
            response.header(HttpHeaders.CacheControl,"no-store")
            return requireNativeActor(tokens)
        }
        authenticate(AuthTokenService.PROVIDER_NAME) {
            get { call.respond(ApiResponse(service.listScoped(call.actor()),call.requestId())) }
            get("/{id}") {
                val actor=call.actor();val id=call.eventId()
                val event=service.getScoped(actor,id) ?: throw ApiException.NotFound("Event was not found")
                call.respond(ApiResponse(event,call.requestId()))
            }
            post {
                val actor=call.actor();val request=call.eventRequest(AcademicEventWriteRequest.serializer())
                call.respond(ApiResponse(service.writeScoped(actor,null,request),call.requestId()))
            }
            patch("/{id}") {
                val actor=call.actor();val id=call.eventId();val request=call.eventRequest(AcademicEventWriteRequest.serializer())
                call.respond(ApiResponse(service.writeScoped(actor,id,request),call.requestId()))
            }
            delete("/{id}") {
                val actor=call.actor();val id=call.eventId();val request=call.eventRequest(AcademicOperationRequest.serializer())
                call.respond(ApiResponse(service.cancelScoped(actor,id,request),call.requestId()))
            }
        }
    }
}
private fun ApplicationCall.eventId():String=parameters["id"]?.takeIf { it.isNotBlank() && it.length<=128 } ?: throw ApiException.Validation("Event ID is required")
private suspend fun <T> ApplicationCall.eventRequest(serializer:DeserializationStrategy<T>):T = try { boundedAuthRequest(serializer) }
catch(cancelled:CancellationException) { throw cancelled }
catch(_:Exception) { throw ApiException.Validation("Invalid event request") }
