package org.companerodeescuela.api.mail

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.companerodeescuela.api.invitations.InvitationEmailGateway
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

/** Provider acceptance is not proof of mailbox delivery. No token or key is logged. */
internal class ResendInvitationEmailGateway(
    private val apiKey: CharArray,
    private val sender: String,
    private val endpoint: URI = URI("https://api.resend.com/emails"),
) : InvitationEmailGateway {
    init {
        require(
            endpoint == URI("https://api.resend.com/emails") ||
                (endpoint.scheme == "http" && endpoint.host == "127.0.0.1" && endpoint.path == "/emails" &&
                    endpoint.rawUserInfo == null && endpoint.rawQuery == null && endpoint.rawFragment == null),
        )
    }

    override suspend fun send(
        recipient: String,
        code: String,
        invitationId: String,
        expiresAt: Instant,
    ): VerificationDeliveryStatus {
        if (!Regex("[A-Za-z0-9_-]{43}").matches(code) ||
            !Regex("[A-Za-z0-9_-]{43}").matches(invitationId)
        ) {
            return VerificationDeliveryStatus.FAILED
        }
        val body =
            buildJsonObject {
                put("from", "Compañero de Clase <$sender>")
                put("to", buildJsonArray { add(recipient) })
                put("subject", "Invitación a Compañero de Clase")
                put(
                    "text",
                    "Te invitaron a Compañero de Clase.\n\n" +
                        "Abre la aplicación y regístrate con este mismo correo. " +
                        "Verifica tu dirección con el correo de confirmación.\n\n" +
                        "Código de invitación: $code\n" +
                        "ID de invitación: $invitationId\n" +
                        "Caduca: $expiresAt (UTC)\n\n" +
                        "Conserva el código hasta aceptar la invitación en la aplicación. " +
                        "La invitación no concede permisos ni privilegios automáticamente. " +
                        "Si no esperabas este correo, ignóralo.",
                )
                put("html", html(code, invitationId, expiresAt))
            }.toString().toByteArray(Charsets.UTF_8)
        return withContext(Dispatchers.IO) { attempt(body, invitationId) }
    }

    private fun html(code: String, id: String, expiresAt: Instant): String =
        """
        <!doctype html><html lang="es"><head><meta charset="utf-8"></head>
        <body style="background:#f5f5f7;margin:0;padding:32px 12px;font-family:Arial,sans-serif;color:#202024">
          <main style="max-width:540px;margin:auto;background:#fff;border-radius:22px;padding:32px">
            <p style="color:#8a1538;font-weight:700">COMPAÑERO DE CLASE</p>
            <h1 style="font-size:26px">Tu invitación está lista</h1>
            <p>Regístrate en la aplicación con este mismo correo y verifica tu cuenta.</p>
            <p style="font-size:12px;color:#595961">CÓDIGO DE INVITACIÓN</p>
            <p style="font-size:20px;letter-spacing:1px;overflow-wrap:anywhere;font-weight:700">$code</p>
            <p style="font-size:12px;color:#595961">ID: $id</p>
            <p style="color:#595961">Válida hasta $expiresAt UTC.</p>
            <hr style="border:0;border-top:1px solid #e8e8eb">
            <p style="font-size:13px;color:#595961">
              Este código se acepta después de verificar el correo. No otorga roles ni permisos por sí solo.
              Si no solicitaste una invitación, ignora este mensaje.
            </p>
          </main>
        </body></html>
        """.trimIndent()

    private fun attempt(payload: ByteArray, id: String): VerificationDeliveryStatus {
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.concatToString())
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Idempotency-Key", "invite-$id")
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            if (code !in 200..299) {
                return if (code == 409 || code == 429 || code >= 500) {
                    VerificationDeliveryStatus.UNCONFIRMED
                } else {
                    VerificationDeliveryStatus.FAILED
                }
            }
            val response =
                connection.inputStream.use { stream ->
                    val buffer = ByteArray(1024)
                    val output = ByteArrayOutputStream()
                    while (output.size() < 16_384) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > 16_384) return VerificationDeliveryStatus.UNCONFIRMED
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8)
                }
            val mailId =
                runCatching { Json.parseToJsonElement(response).jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                    .getOrNull()
            if (mailId.isNullOrBlank() || mailId.length > 128) {
                VerificationDeliveryStatus.UNCONFIRMED
            } else {
                VerificationDeliveryStatus.ACCEPTED
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            VerificationDeliveryStatus.UNCONFIRMED
        } finally {
            connection.disconnect()
        }
    }

    override fun toString() = "ResendInvitationEmailGateway(configured=true)"
}
