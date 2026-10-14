package org.companerodeescuela.api.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import org.companerodeescuela.api.plugins.ApiJson
import org.companerodeescuela.api.errors.ApiException

/** Limits allocation before JSON decoding, including bodies without Content-Length. */
internal const val DEFAULT_BODY_MAX_BYTES = 64 * 1024
internal const val AUTH_BODY_MAX_BYTES = 4 * 1024

internal suspend fun <T> ApplicationCall.boundedBody(
    serializer: DeserializationStrategy<T>,
    maxBytes: Int = DEFAULT_BODY_MAX_BYTES,
): T {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val channel = receiveChannel()
    val bytes = ByteArray(maxBytes + 1)
    var size = 0
    while (size < bytes.size) {
        val count = channel.readAvailable(bytes, size, bytes.size - size)
        if (count < 0) break
        size += count
    }
    if (size > maxBytes) {
        channel.cancel(null)
        throw ApiException.Validation("Request body is too large")
    }
    return try {
        ApiJson.decodeFromString(serializer, bytes.decodeToString(0, size, throwOnInvalidSequence = true))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: ApiException) {
        throw error
    } catch (_: Exception) {
        throw ApiException.Validation("Invalid request body")
    }
}

internal suspend fun <T> ApplicationCall.boundedAuthRequest(serializer: DeserializationStrategy<T>): T =
    boundedBody(serializer)
