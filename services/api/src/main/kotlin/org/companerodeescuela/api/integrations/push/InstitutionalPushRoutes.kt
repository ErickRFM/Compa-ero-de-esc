package org.companerodeescuela.api.integrations.push

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.plugins.clientAddress
import org.companerodeescuela.shared.contracts.InstitutionalPushReceipt
import java.time.Clock

const val INTEGRATION_KEY_ID_HEADER = "X-Integration-Key-Id"
const val INTEGRATION_TIMESTAMP_HEADER = "X-Integration-Timestamp"
const val INTEGRATION_NONCE_HEADER = "X-Integration-Nonce"
const val INTEGRATION_SIGNATURE_HEADER = "X-Integration-Signature"

fun Route.institutionalPushRoutes(
    authenticator: InstitutionalPushAuthenticator?,
    service: InstitutionalPushService?,
    rateLimiter: InstitutionalPushRateLimiter?,
    receiptRepository: InstitutionalProjectionReceiptRepository? = null,
    clock: Clock = Clock.systemUTC(),
) {
    route("/integration/v1") {
        post("/events") {
            if (authenticator == null || service == null || rateLimiter == null) {
                call.respond(HttpStatusCode.ServiceUnavailable)
                return@post
            }
            val retryAfterSeconds = rateLimiter.acquire(call.clientAddress())
            if (retryAfterSeconds != null) {
                call.response.header(HttpHeaders.RetryAfter, retryAfterSeconds.toString())
                call.respond(HttpStatusCode.TooManyRequests)
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
        post("/projection-status") {
            if (authenticator == null || receiptRepository == null) {
                call.respond(HttpStatusCode.ServiceUnavailable)
                return@post
            }
            if (!call.request.contentType().match(ContentType.Application.Json)) {
                call.respond(HttpStatusCode.UnsupportedMediaType)
                return@post
            }
            val declaredLength = call.request.headers["Content-Length"]?.toLongOrNull()
            if (declaredLength != null && declaredLength > MAX_PROJECTION_STATUS_BODY_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge)
                return@post
            }
            val boundedBody = call.readBoundedBody(MAX_PROJECTION_STATUS_BODY_BYTES)
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
            val request =
                try {
                    ProjectionStatusJson.decodeFromString<ProjectionStatusRequest>(String(body, Charsets.UTF_8))
                } catch (_: SerializationException) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                } catch (_: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            if (request.contract != PROJECTION_STATUS_REQUEST_CONTRACT ||
                request.eventIds.size !in 1..MAX_PROJECTION_STATUS_EVENT_IDS ||
                request.eventIds.any { !UUID_EVENT_ID.matches(it) } ||
                request.eventIds.distinct().size != request.eventIds.size
            ) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val receipts = receiptRepository.findReceipts(institution.tenantId, request.eventIds)
            call.respond(
                HttpStatusCode.OK,
                ProjectionStatusResponse(PROJECTION_STATUS_CONTRACT, clock.instant().epochSecond, receipts),
            )
        }
    }
}

@Serializable
private data class ProjectionStatusRequest(
    val contract: String,
    val eventIds: List<String>,
)

@Serializable
private data class ProjectionStatusResponse(
    val contract: String,
    val observedAtEpochSeconds: Long,
    val receipts: List<InstitutionalProjectionReceipt>,
)

private val ProjectionStatusJson =
    Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }
private val UUID_EVENT_ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
private const val PROJECTION_STATUS_REQUEST_CONTRACT = "siii.projection_status_request.v1"
private const val PROJECTION_STATUS_CONTRACT = "siii.projection_status.v1"
private const val MAX_PROJECTION_STATUS_EVENT_IDS = 50
private const val MAX_PROJECTION_STATUS_BODY_BYTES = 32_768

private data class BoundedPushBody(
    val bytes: ByteArray?,
    val tooLarge: Boolean,
)

private suspend fun io.ktor.server.application.ApplicationCall.readBoundedBody(
    maxBytes: Int = InstitutionalPushService.MAX_BODY_BYTES,
): BoundedPushBody {
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
