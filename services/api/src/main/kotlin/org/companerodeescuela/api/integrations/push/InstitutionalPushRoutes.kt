package org.companerodeescuela.api.integrations.push

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readAvailable
import org.companerodeescuela.shared.contracts.InstitutionalPushReceipt

const val INTEGRATION_KEY_ID_HEADER = "X-Integration-Key-Id"
const val INTEGRATION_TIMESTAMP_HEADER = "X-Integration-Timestamp"
const val INTEGRATION_NONCE_HEADER = "X-Integration-Nonce"
const val INTEGRATION_SIGNATURE_HEADER = "X-Integration-Signature"

fun Route.institutionalPushRoutes(
    authenticator: InstitutionalPushAuthenticator?,
    service: InstitutionalPushService?,
) {
    route("/integration/v1") {
        post("/events") {
            if (authenticator == null || service == null) {
                call.respond(HttpStatusCode.ServiceUnavailable)
                return@post
            }
            if (!call.request.contentType().match(ContentType.Application.Json)) {
                call.respond(HttpStatusCode.UnsupportedMediaType)
                return@post
            }
            val declaredLength = call.request.headers["Content-Length"]?.toLongOrNull()
            if (declaredLength != null && declaredLength > InstitutionalPushService.MAX_BODY_BYTES.toLong()) {
                call.respond(HttpStatusCode.PayloadTooLarge)
                return@post
            }
            val boundedBody = call.readBoundedBody()
            if (boundedBody.tooLarge) {
                call.respond(HttpStatusCode.PayloadTooLarge)
                return@post
            }
            val body = boundedBody.bytes
            if (body == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val signed =
                SignedIntegrationRequest(
                    keyId = call.request.headers[INTEGRATION_KEY_ID_HEADER].orEmpty(),
                    timestamp = call.request.headers[INTEGRATION_TIMESTAMP_HEADER].orEmpty(),
                    nonce = call.request.headers[INTEGRATION_NONCE_HEADER].orEmpty(),
                    signature = call.request.headers[INTEGRATION_SIGNATURE_HEADER].orEmpty(),
                    method = call.request.local.method.value,
                    path = call.request.path(),
                    body = body,
                )
            val institution = authenticator.authenticate(signed)
            if (institution == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            when (val result = service.accept(institution, body)) {
                is InstitutionalPushResult.Accepted ->
                    call.respond(
                        if (result.duplicate) HttpStatusCode.OK else HttpStatusCode.Accepted,
                        InstitutionalPushReceipt(
                            eventId = result.eventId,
                            status = if (result.duplicate) "duplicate" else "received",
                            receivedAtEpochSeconds = result.receivedAt.epochSecond,
                        ),
                    )
                InstitutionalPushResult.Invalid -> call.respond(HttpStatusCode.BadRequest)
                InstitutionalPushResult.Forbidden -> call.respond(HttpStatusCode.Forbidden)
                InstitutionalPushResult.Conflict -> call.respond(HttpStatusCode.Conflict)
                InstitutionalPushResult.Stale -> call.respond(HttpStatusCode.Conflict)
            }
        }
    }
}

private data class BoundedPushBody(
    val bytes: ByteArray?,
    val tooLarge: Boolean,
)

private suspend fun io.ktor.server.application.ApplicationCall.readBoundedBody(): BoundedPushBody {
    val maxBytes = InstitutionalPushService.MAX_BODY_BYTES
    val channel = receiveChannel()
    val buffer = ByteArray(maxBytes + 1)
    var size = 0
    while (size < buffer.size) {
        val read = channel.readAvailable(buffer, size, buffer.size - size)
        if (read < 0) break
        size += read
    }
    if (size > maxBytes) {
        channel.cancel(null)
        return BoundedPushBody(null, tooLarge = true)
    }
    return BoundedPushBody(buffer.copyOf(size).takeIf { it.isNotEmpty() }, tooLarge = false)
}
