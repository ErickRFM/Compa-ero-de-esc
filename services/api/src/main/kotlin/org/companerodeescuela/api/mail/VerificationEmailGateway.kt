package org.companerodeescuela.api.mail

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus

fun interface VerificationEmailGateway {
    suspend fun send(
        recipient: String,
        token: String,
        operationId: String,
    ): VerificationDeliveryStatus
}

object UnavailableVerificationEmailGateway : VerificationEmailGateway {
    override suspend fun send(
        recipient: String,
        token: String,
        operationId: String,
    ) = VerificationDeliveryStatus.UNAVAILABLE
}

/** Real provider adapter; no API key, recipient, token or provider response body is logged. */
class ResendVerificationEmailGateway(
    private val apiKey: CharArray,
    private val sender: String,
    private val endpoint: URI = URI("https://api.resend.com/emails"),
) : VerificationEmailGateway {
    init {
        require(
            endpoint == URI("https://api.resend.com/emails") ||
                (
                    endpoint.scheme == "http" &&
                        endpoint.host == "127.0.0.1" &&
                        endpoint.path == "/emails" &&
                        endpoint.rawUserInfo == null &&
                        endpoint.rawQuery == null &&
                        endpoint.rawFragment == null
                ),
        )
    }

    // Keep the email markup outside Kotlin source and package it as an application resource.
    private val template =
        requireNotNull(javaClass.getResource("/mail/verification-email.html")) {
            "Verification email template is required"
        }.readText(Charsets.UTF_8)

    override suspend fun send(
        recipient: String,
        token: String,
        operationId: String,
    ): VerificationDeliveryStatus {
        val payload =
            buildJsonObject {
                put("from", "Compañero de Clase <$sender>")
                put("to", buildJsonArray { add(recipient) })
                put("subject", "Verifica tu correo en Compañero de Clase / Verify your email")
                put(
                    "text",
                    "Pega este código en la aplicación / Paste this code in the app:\n\n$token\n\n" +
                        "Caduca en una hora / Expires in one hour. " +
                        "Si no creaste esta cuenta, ignora este correo / If you did not create this account, ignore this email.",
                )
                put("html", verificationHtml(token))
            }.toString().toByteArray(Charsets.UTF_8)
        var unresolvedEarlierAttempt = false
        repeat(2) { attemptNumber ->
            val (terminal, status) = withContext(Dispatchers.IO) { attempt(payload, operationId) }
            if (status == VerificationDeliveryStatus.UNCONFIRMED) unresolvedEarlierAttempt = true
            if (terminal || attemptNumber == 1) {
                return if (unresolvedEarlierAttempt && status == VerificationDeliveryStatus.FAILED) {
                    VerificationDeliveryStatus.UNCONFIRMED
                } else {
                    status
                }
            }
            delay(250)
        }
        return VerificationDeliveryStatus.UNCONFIRMED
    }

    private fun verificationHtml(token: String): String {
        val code =
            token
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        return template.replace("__VERIFICATION_CODE__", code)
    }

    private fun attempt(
        payload: ByteArray,
        operationId: String,
    ): Pair<Boolean, VerificationDeliveryStatus> {
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.concatToString())
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Idempotency-Key", operationId)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }

            val code = connection.responseCode
            if (code !in 200..299) {
                // 409, throttling and transient errors never prove that a message was not delivered.
                val uncertain = code == 409 || code == 429 || code >= 500
                !uncertain to
                    if (uncertain) {
                        VerificationDeliveryStatus.UNCONFIRMED
                    } else {
                        VerificationDeliveryStatus.FAILED
                    }
            } else {
                val body =
                    connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        val deadline = System.nanoTime() + 10_000_000_000L
                        while (true) {
                            if (System.nanoTime() >= deadline) {
                                return true to VerificationDeliveryStatus.UNCONFIRMED
                            }
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 16_384) {
                                return true to VerificationDeliveryStatus.UNCONFIRMED
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8)
                    }
                val id =
                    runCatching {
                        val response = Json.parseToJsonElement(body)
                        val messageId = response.jsonObject["id"]
                        messageId?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                val status =
                    if (!id.isNullOrBlank() && id.length <= 128) {
                        VerificationDeliveryStatus.ACCEPTED
                    } else {
                        VerificationDeliveryStatus.UNCONFIRMED
                    }
                true to status
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false to VerificationDeliveryStatus.UNCONFIRMED
        } finally {
            connection.disconnect()
        }
    }

    override fun toString(): String = "ResendVerificationEmailGateway(configured=true)"
}
