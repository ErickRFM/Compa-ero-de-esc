package org.companerodeescuela.api.mail

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus

fun interface VerificationEmailGateway {
    suspend fun send(recipient: String, token: String, operationId: String): VerificationDeliveryStatus
}
object UnavailableVerificationEmailGateway : VerificationEmailGateway {
    override suspend fun send(recipient: String, token: String, operationId: String) = VerificationDeliveryStatus.UNAVAILABLE
}
/** Real provider adapter; no API key, recipient, token or provider body is logged. */
class ResendVerificationEmailGateway(private val apiKey: CharArray, private val sender: String,
    private val endpoint: URI = URI("https://api.resend.com/emails")) : VerificationEmailGateway {
    init {
        require(endpoint == URI("https://api.resend.com/emails") ||
            (endpoint.scheme == "http" && endpoint.host == "127.0.0.1" && endpoint.path == "/emails" && endpoint.rawUserInfo == null && endpoint.rawQuery == null && endpoint.rawFragment == null))
    }
    override suspend fun send(recipient: String, token: String, operationId: String): VerificationDeliveryStatus {
        val payload = buildJsonObject {
            put("from", "Compañero de Clase <$sender>")
            put("to", buildJsonArray { add(recipient) })
            put("subject", "Verifica tu correo en Compañero de Clase / Verify your email")
            put("text", "Pega este código en la aplicación / Paste this code in the app:\n\n$token\n\nCaduca en una hora / Expires in one hour. Si no creaste esta cuenta, ignora este correo / If you did not create this account, ignore this email.")
            put("html", verificationHtml(token))
        }.toString().toByteArray(Charsets.UTF_8)
        var unresolvedEarlierAttempt = false
        repeat(2) { attemptNumber ->
            val result = withContext(Dispatchers.IO) { attempt(payload, operationId) }
            if (result.second == VerificationDeliveryStatus.UNCONFIRMED) unresolvedEarlierAttempt = true
            if (result.first || attemptNumber == 1) return if (unresolvedEarlierAttempt && result.second == VerificationDeliveryStatus.FAILED)
                VerificationDeliveryStatus.UNCONFIRMED else result.second
            delay(250)
        }
        return VerificationDeliveryStatus.UNCONFIRMED
    }

    private fun verificationHtml(token: String): String {
        val code = token.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
        return """
            <!DOCTYPE html>
            <html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
            <meta http-equiv="X-UA-Compatible" content="IE=edge"><title>Verifica tu correo · Compañero</title></head>
            <body style="margin:0;padding:0;background-color:#F3F1F2;font-family:Arial,Helvetica,sans-serif;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#F3F1F2;">
              <tr><td align="center" style="padding-top:32px;padding-right:16px;padding-bottom:32px;padding-left:16px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;background-color:#FFFFFF;border-radius:20px;overflow:hidden;">
                <tr><td bgcolor="#191A1C" style="background-color:#191A1C;padding-top:30px;padding-right:32px;padding-bottom:30px;padding-left:32px;">
                  <p style="margin:0;color:#FFFFFF;font-size:22px;line-height:30px;font-weight:700;">COMPAÑERO <span style="color:#EBA1B7;">●</span></p>
                  <p style="margin-top:6px;margin-bottom:0;color:#C7C5C8;font-size:12px;line-height:20px;letter-spacing:1px;">TU VIDA ACADÉMICA, EN UN LUGAR</p>
                </td></tr>
                <tr><td style="padding-top:36px;padding-right:32px;padding-bottom:14px;padding-left:32px;">
                  <p style="margin:0;color:#8A1538;font-size:12px;line-height:18px;font-weight:700;letter-spacing:1px;">SEGURIDAD DE TU CUENTA</p>
                  <h1 style="margin-top:12px;margin-bottom:12px;color:#191A1C;font-size:28px;line-height:36px;">Confirma que eres tú.</h1>
                  <p style="margin:0;color:#5B5C60;font-size:15px;line-height:24px;">Estás a un paso de acceder a Compañero de Clase. Introduce este código en la aplicación para verificar tu correo.</p>
                </td></tr>
                <tr><td style="padding-top:15px;padding-right:32px;padding-bottom:20px;padding-left:32px;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                    <tr><td align="center" bgcolor="#F9EFF2" style="background-color:#F9EFF2;border-radius:14px;padding-top:24px;padding-right:12px;padding-bottom:24px;padding-left:12px;">
                      <p style="margin:0;color:#8A1538;font-size:12px;line-height:18px;font-weight:700;">TU CÓDIGO DE VERIFICACIÓN</p>
                      <p style="margin-top:12px;margin-bottom:0;color:#191A1C;font-size:30px;line-height:38px;font-weight:700;letter-spacing:3px;overflow-wrap:anywhere;">${code}</p>
                    </td></tr>
                  </table>
                </td></tr>
                <tr><td style="padding-top:0;padding-right:32px;padding-bottom:30px;padding-left:32px;">
                  <p style="margin-top:0;margin-bottom:12px;color:#44454A;font-size:14px;line-height:22px;"><strong>Caduca en una hora.</strong> No compartas este código con nadie.</p>
                  <p style="margin:0;color:#74757A;font-size:13px;line-height:21px;">Si no solicitaste esta verificación, ignora este mensaje. Tu cuenta seguirá protegida.</p>
                </td></tr>
                <tr><td bgcolor="#F7F7F8" style="background-color:#F7F7F8;padding-top:20px;padding-right:32px;padding-bottom:22px;padding-left:32px;">
                  <p style="margin:0;color:#7B7C80;font-size:12px;line-height:19px;">Compañero de Clase · Mensaje automático de seguridad.<br>Do not share your verification code.</p>
                </td></tr>
              </table></td></tr>
            </table></body></html>
        """.trimIndent()
    }

    private fun attempt(payload: ByteArray, operationId: String): Pair<Boolean, VerificationDeliveryStatus> {
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false; connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.concatToString())
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Idempotency-Key", operationId)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            if (code !in 200..299) {
                // An in-progress idempotent request, throttle or server failure cannot establish non-delivery.
                val uncertain = code == 409 || code == 429 || code >= 500
                return !uncertain to if (uncertain) VerificationDeliveryStatus.UNCONFIRMED else VerificationDeliveryStatus.FAILED
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(1024)
                val deadline = System.nanoTime() + 10_000_000_000L
                while (true) {
                    if (System.nanoTime() >= deadline) return true to VerificationDeliveryStatus.UNCONFIRMED
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 16_384) return true to VerificationDeliveryStatus.UNCONFIRMED
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8)
            }
            val id = runCatching { Json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            true to if (!id.isNullOrBlank() && id.length <= 128) VerificationDeliveryStatus.ACCEPTED else VerificationDeliveryStatus.UNCONFIRMED
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false to VerificationDeliveryStatus.UNCONFIRMED }
        finally { connection.disconnect() }
    }
    override fun toString() = "ResendVerificationEmailGateway(configured=true)"
}
