package org.companerodeescuela.api.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.DeserializationStrategy
import org.companerodeescuela.api.plugins.ApiJson
import org.companerodeescuela.api.errors.ApiException

/** Limits allocation before JSON decoding, including bodies without Content-Length. */
internal suspend fun <T> ApplicationCall.boundedAuthRequest(serializer: DeserializationStrategy<T>): T {
    val channel = receiveChannel()
    val bytes = ByteArray(4097)
    var size = 0
    while (size < bytes.size) {
        val count = channel.readAvailable(bytes, size, bytes.size - size)
        if (count < 0) break
        size += count
    }
    if (size > 4096) {
        channel.cancel(null)
        throw ApiException.Validation("Authentication request is too large")
    }
    return ApiJson.decodeFromString(serializer, bytes.decodeToString(0, size, throwOnInvalidSequence = true))
}
