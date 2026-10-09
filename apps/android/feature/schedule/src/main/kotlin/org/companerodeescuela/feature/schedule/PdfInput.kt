package org.companerodeescuela.feature.schedule

import java.io.InputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException

/** The caller owns and closes the stream. */
internal fun readPdfBytes(input: InputStream, maxBytes: Int, checkActive: () -> Unit = {}): ByteArray? {
    require(maxBytes >= 0 && maxBytes < Int.MAX_VALUE)
    val bytes = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(minOf(maxBytes + 1, DEFAULT_BUFFER_SIZE))
    while (true) {
        checkActive()
        val remaining = maxBytes - bytes.size()
        val count = input.read(buffer, 0, minOf(buffer.size, remaining + 1))
        when {
            count < 0 -> return bytes.toByteArray()
            count == 0 -> {
                val value = input.read()
                if (value < 0) return bytes.toByteArray()
                if (remaining == 0) return null
                bytes.write(value)
            }
            count > remaining -> return null
            else -> bytes.write(buffer, 0, count)
        }
    }
}

internal suspend fun <T> readNativePdfOrNull(read: suspend () -> T): T? = try {
    read()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
