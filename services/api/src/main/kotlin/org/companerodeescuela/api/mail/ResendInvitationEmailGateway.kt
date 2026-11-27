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
import org.companerodeescuela.api.invitations.InvitationEmailKind
import org.companerodeescuela.api.invitations.InvitationProfile
import org.companerodeescuela.shared.contracts.VerificationDeliveryStatus
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
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

    override suspend fun send(
        recipient: String,
        code: String,
        invitationId: String,
        expiresAt: Instant,
    ): VerificationDeliveryStatus = sendWithKind(recipient, code, invitationId, expiresAt, InvitationEmailKind.REGISTRATION, null)

    override suspend fun sendRegistration(
        recipient: String,
        code: String,
        invitationId: String,
        expiresAt: Instant,
        profile: InvitationProfile?,
    ): VerificationDeliveryStatus = sendWithKind(recipient, code, invitationId, expiresAt, InvitationEmailKind.REGISTRATION, profile)

    override suspend fun sendOwnerBootstrap(
        recipient: String,
        code: String,
        invitationId: String,
        expiresAt: Instant,
    ): VerificationDeliveryStatus = sendWithKind(recipient, code, invitationId, expiresAt, InvitationEmailKind.OWNER_BOOTSTRAP, null)

    private suspend fun sendWithKind(
        recipient: String,
        code: String,
        invitationId: String,
        expiresAt: Instant,
        kind: InvitationEmailKind,
        profile: InvitationProfile?,
    ): VerificationDeliveryStatus {
        if (!Regex("[A-Za-z0-9_-]{43}").matches(code) ||
            !Regex("[A-Za-z0-9_-]{43}").matches(invitationId)
        ) {
            return VerificationDeliveryStatus.FAILED
        }
        val ownerBootstrap = kind == InvitationEmailKind.OWNER_BOOTSTRAP
        val profileLabel = profile?.spanishLabel()
        val combinedKey = "$invitationId.$code"
        val subject =
            if (ownerBootstrap) {
                "Invitación de propietario a Compañero de Clase"
            } else if (profileLabel != null) {
                "Invitación ($profileLabel) a Compañero de Clase"
            } else {
                "Invitación a Compañero de Clase"
            }
        val category = if (ownerBootstrap) "companero_owner_invitation" else "companero_invitation"
        val introduction =
            if (ownerBootstrap) {
                "Recibiste una invitación única para convertir tu cuenta existente en la cuenta propietaria de Compañero de Clase.\n\n" +
                    "Abre la aplicación e inicia el registro OWNER_INVITED con este mismo correo y tu contraseña actual. " +
                    "Tu cuenta verificada y su MFA se conservarán.\n\n"
            } else {
                val roleNote = if (profileLabel != null) "Perfil invitado: $profileLabel.\n" else ""
                "Te invitaron a Compañero de Clase.\n" +
                    roleNote +
                    "\nAbre la aplicación, entra en 'Crear cuenta / Tengo invitación' y regístrate con este mismo correo. " +
                    "Copia y pega la clave completa de activación y verifica tu dirección con el correo de confirmación.\n\n"
            }
        val textKeys =
            if (ownerBootstrap) {
                "Código de invitación: $code\n" +
                    "ID de invitación: $invitationId\n"
            } else {
                "Clave completa de activación (copiar y pegar): $combinedKey\n" +
                    "Código de invitación: $code\n" +
                    "ID de invitación: $invitationId\n"
            }
        val body =
            buildJsonObject {
                put("from", "Compañero de Clase <$sender>")
                put("to", buildJsonArray { add(recipient) })
                put("subject", subject)
                put(
                    "tags",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("name", "category")
                                put("value", category)
                            },
                        )
                        add(
                            buildJsonObject {
                                put("name", "invitation_id")
                                put("value", invitationId)
                            },
                        )
                    },
                )
                put(
                    "text",
                    introduction +
                        textKeys +
                        "Caduca: $expiresAt (UTC)\n\n" +
                        "Conserva el código hasta aceptar la invitación en la aplicación. " +
                        "La invitación no concede permisos ni privilegios automáticamente. " +
                        "Si no esperabas este correo, ignóralo.",
                )
                put("html", html(code, invitationId, combinedKey, expiresAt, kind, profileLabel))
            }.toString().toByteArray(Charsets.UTF_8)
        return withContext(Dispatchers.IO) { attempt(body) }
    }

    private fun html(
        code: String,
        id: String,
        combinedKey: String,
        expiresAt: Instant,
        kind: InvitationEmailKind,
        profileLabel: String?,
    ): String {
        val ownerBootstrap = kind == InvitationEmailKind.OWNER_BOOTSTRAP
        val roleBlock =
            if (!ownerBootstrap && profileLabel != null) {
                """
                <p style="margin:8px 0 14px;padding:8px 12px;background:#f7edf0;border-radius:10px;color:#8a1538;font-weight:700;display:inline-block">
                  Perfil invitado: $profileLabel
                </p>
                """.trimIndent()
            } else {
                ""
            }
        val keyBlock =
            if (ownerBootstrap) {
                """
                <p style="font-size:12px;color:#595961">CÓDIGO DE INVITACIÓN</p>
                <p style="font-size:20px;letter-spacing:1px;overflow-wrap:anywhere;font-weight:700">$code</p>
                <p style="font-size:12px;color:#595961">ID: $id</p>
                """.trimIndent()
            } else {
                """
                <p style="font-size:12px;color:#595961;font-weight:700;margin-bottom:6px">CLAVE DE ACTIVACIÓN (COPIAR Y PEGAR EN LA APP)</p>
                <p style="font-size:16px;padding:14px;background:#f5f5f7;border:1px solid #dcdce2;border-radius:12px;overflow-wrap:anywhere;font-family:monospace;font-weight:700">$combinedKey</p>
                <p style="font-size:12px;color:#595961;margin-top:14px">Datos individuales (si los ingresas por separado):</p>
                <p style="font-size:13px;color:#595961;overflow-wrap:anywhere;margin:4px 0">Código: <strong>$code</strong></p>
                <p style="font-size:12px;color:#595961;overflow-wrap:anywhere;margin:4px 0">ID: $id</p>
                """.trimIndent()
            }
        return """
            <!doctype html><html lang="es"><head><meta charset="utf-8"></head>
            <body style="background:#f5f5f7;margin:0;padding:32px 12px;font-family:Arial,sans-serif;color:#202024">
              <main style="max-width:540px;margin:auto;background:#fff;border-radius:22px;padding:32px">
                <p style="color:#8a1538;font-weight:700">COMPAÑERO DE CLASE</p>
                <h1 style="font-size:26px">${if (ownerBootstrap) "Invitación de propietario" else "Tu invitación está lista"}</h1>
                $roleBlock
                <p>${if (ownerBootstrap) "Inicia el registro OWNER_INVITED con este mismo correo y tu contraseña actual. Tu cuenta verificada y su MFA se conservarán." else "Abre Compañero Gestión o Académica, toca «Tengo invitación», pega la clave de activación y regístrate con este mismo correo."}</p>
                $keyBlock
                <p style="color:#595961">Válida hasta $expiresAt UTC.</p>
                <hr style="border:0;border-top:1px solid #e8e8eb">
                <p style="font-size:13px;color:#595961">
                  La invitación se valida al verificar tu correo y requiere autorización final del administrador. No otorga roles ni permisos por sí sola.
                  Si no solicitaste una invitación, ignora este mensaje.
                </p>
              </main>
            </body></html>
            """.trimIndent()
    }

    private fun InvitationProfile.spanishLabel(): String =
        when (this) {
            InvitationProfile.INSTITUTION_ADMIN -> "Administrador institucional"
            InvitationProfile.TEACHER -> "Docente"
            InvitationProfile.TUTOR -> "Tutor"
            InvitationProfile.STUDENT -> "Alumno"
            InvitationProfile.PARTICIPANT -> "Participante"
            InvitationProfile.EXTERNAL_GUEST -> "Invitado externo"
        }

    private fun attempt(payload: ByteArray): VerificationDeliveryStatus {
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.concatToString())
            connection.setRequestProperty("Content-Type", "application/json")
            // A rotated invitation code changes the message payload. Reusing the invitation ID
            // as the idempotency key would cause Resend to replay the *old* delivery,
            // making a newly issued code undeliverable. Equal payloads remain deduplicated.
            val payloadDigest =
                MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
            connection.setRequestProperty("Idempotency-Key", "invite-v2-$payloadDigest")
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
                runCatching {
                    Json
                        .parseToJsonElement(response)
                        .jsonObject["id"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                }.getOrNull()
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
