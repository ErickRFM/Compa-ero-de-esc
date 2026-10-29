package org.companerodeescuela.api.integrations.push

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.plugins.clientAddress
import org.companerodeescuela.shared.contracts.InstitutionalAttendanceHistoryContract
import org.companerodeescuela.shared.contracts.InstitutionalAttendanceHistoryRequest
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

private const val MAX_HISTORY_BODY_BYTES = 4_096
private const val HISTORY_TIMEOUT_MILLIS = 10_000L
private val HistoryJson =
    Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

fun Route.institutionalAttendanceHistoryRoutes(
    authenticator: InstitutionalPushAuthenticator?,
    service: InstitutionalAttendanceHistoryService?,
    rateLimiter: InstitutionalPushRateLimiter?,
) {
    post(InstitutionalPushAuthenticator.ATTENDANCE_HISTORY_PATH) {
        call.response.header(HttpHeaders.CacheControl, "no-store")
        try {
            withTimeout(HISTORY_TIMEOUT_MILLIS) { call.attendanceHistory(authenticator, service, rateLimiter) }
        } catch (_: TimeoutCancellationException) {
            throw ApiException.DependencyUnavailable("Institutional attendance history is temporarily unavailable")
        }
    }
}

private suspend fun ApplicationCall.attendanceHistory(
    authenticator: InstitutionalPushAuthenticator?,
    service: InstitutionalAttendanceHistoryService?,
    rateLimiter: InstitutionalPushRateLimiter?,
) {
    if (authenticator == null || service == null || rateLimiter == null) {
        respond(HttpStatusCode.ServiceUnavailable)
        return
    }
    rateLimiter.acquire(clientAddress())?.let { seconds ->
        response.header(HttpHeaders.RetryAfter, seconds.toString())
        respond(HttpStatusCode.TooManyRequests)
        return
    }
    if (!request.contentType().match(ContentType.Application.Json)) {
        respond(HttpStatusCode.UnsupportedMediaType)
        return
    }
    if (request.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { it > MAX_HISTORY_BODY_BYTES } == true) {
        respond(HttpStatusCode.PayloadTooLarge)
        return
    }
    val channel = receiveChannel()
    val buffer = ByteArray(MAX_HISTORY_BODY_BYTES + 1)
    var size = 0
    while (size < buffer.size) {
        val read = channel.readAvailable(buffer, size, buffer.size - size)
        if (read < 0) break
        size += read
    }
    if (size > MAX_HISTORY_BODY_BYTES) {
        channel.cancel(null)
        respond(HttpStatusCode.PayloadTooLarge)
        return
    }
    val body = buffer.copyOf(size)
    val institution =
        authenticator.authenticate(
            SignedIntegrationRequest(
                request.headers[INTEGRATION_KEY_ID_HEADER].orEmpty(),
                request.headers[INTEGRATION_TIMESTAMP_HEADER].orEmpty(),
                request.headers[INTEGRATION_NONCE_HEADER].orEmpty(),
                request.headers[INTEGRATION_SIGNATURE_HEADER].orEmpty(),
                request.local.method.value,
                request.path(),
                body,
            ),
        )
    if (institution == null) {
        respond(HttpStatusCode.Unauthorized)
        return
    }
    if (InstitutionalAttendanceHistoryContract.SCOPE !in institution.scopes) {
        respond(HttpStatusCode.Forbidden)
        return
    }
    val input =
        try {
            val text =
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString()
            HistoryJson.decodeFromString<InstitutionalAttendanceHistoryRequest>(text)
        } catch (_: CharacterCodingException) {
            respond(HttpStatusCode.BadRequest)
            return
        } catch (_: SerializationException) {
            respond(HttpStatusCode.BadRequest)
            return
        } catch (_: IllegalArgumentException) {
            respond(HttpStatusCode.BadRequest)
            return
        }
    respond(service.read(institution, input))
}
