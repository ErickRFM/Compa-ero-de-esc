package org.companerodeescuela.api.mail

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/** Public callback; trust is established only by a valid Svix signature. */
internal fun Route.resendWebhookRoutes(
    signingSecret: CharArray?,
    database: MongoDatabase?,
) {
    val verifier = signingSecret?.let(::ResendWebhookSignatureVerifier)
    val repository = database?.let(::MongoResendWebhookAuditRepository)

    post("/webhooks/resend") {
        if (verifier == null || repository == null) {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return@post
        }

        val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_WEBHOOK_BODY_BYTES) {
            call.respond(HttpStatusCode.PayloadTooLarge)
            return@post
        }

        val rawBody =
            call.receiveStream().use { input ->
                input.readNBytes(MAX_WEBHOOK_BODY_BYTES + 1)
            }
        if (rawBody.isEmpty() || rawBody.size > MAX_WEBHOOK_BODY_BYTES) {
            call.respond(HttpStatusCode.PayloadTooLarge)
            return@post
        }

        val messageId = call.request.headers["svix-id"]
        val timestamp = call.request.headers["svix-timestamp"]
        val signature = call.request.headers["svix-signature"]
        if (!verifier.verify(rawBody, messageId, timestamp, signature)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val event =
            runCatching {
                val root = Json.parseToJsonElement(rawBody.toString(Charsets.UTF_8)).jsonObject
                val data = root["data"]?.jsonObject ?: error("Missing data")
                val type = root["type"]?.jsonPrimitive?.contentOrNull ?: error("Missing event type")
                val createdAt =
                    root["created_at"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse)
                        ?: error("Missing event timestamp")
                val emailId = data["email_id"]?.jsonPrimitive?.contentOrNull
                if (type.startsWith("email.") && emailId.isNullOrBlank()) error("Missing provider email ID")
                val rawInvitationId = extractResendInvitationId(data["tags"])
                val invitationId = rawInvitationId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) }
                VerifiedResendWebhookEvent(
                    messageId = requireNotNull(messageId),
                    type =
                        type.takeIf { it.matches(Regex("[a-z]+\\.[a-z_]{1,48}")) }
                            ?: error("Invalid event type"),
                    emailId = emailId?.takeIf { it.length <= 128 },
                    invitationId = invitationId,
                    createdAt = createdAt,
                    receivedAt = Instant.now(),
                )
            }.getOrNull()

        if (event == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        repository.record(event)
        call.respond(HttpStatusCode.OK, mapOf("received" to true))
    }
}

private const val MAX_WEBHOOK_BODY_BYTES = 32 * 1024


/** Resend encodes tags as [{ "name": "...", "value": "..." }]. */
internal fun extractResendInvitationId(tags: JsonElement?): String? =
    runCatching {
        tags?.jsonArray?.firstNotNullOfOrNull { tag ->
            val fields = tag.jsonObject
            if (fields["name"]?.jsonPrimitive?.contentOrNull == "invitation_id") {
                fields["value"]?.jsonPrimitive?.contentOrNull
            } else {
                null
            }
        }
    }.getOrNull()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) }
