package org.companerodeescuela.api.devices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.companerodeescuela.api.config.ConfigurationException
import org.companerodeescuela.shared.contracts.AttendancePushContract
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.util.Base64

enum class PushDeliveryResult { ACCEPTED, INVALID_TOKEN, RETRYABLE, CONFIGURATION_ERROR, REJECTED }

fun interface PushGateway {
    suspend fun send(
        token: String,
        title: String,
        body: String,
    ): PushDeliveryResult

    /** Existing providers remain source-compatible; data-aware gateways override this overload. */
    suspend fun send(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>,
    ): PushDeliveryResult = send(token, title, body)
}

object UnavailablePushGateway : PushGateway {
    override suspend fun send(
        token: String,
        title: String,
        body: String,
    ) = PushDeliveryResult.CONFIGURATION_ERROR
}

/** Parsed only at startup. Secrets never enter log output, HTTP responses or JSON serialization. */
class FirebaseServiceAccount private constructor(
    val projectId: String,
    val clientEmail: String,
    private val privateKey: PrivateKey,
) {
    fun sign(message: ByteArray): ByteArray =
        Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(message)
            sign()
        }

    companion object {
        fun parse(json: String): FirebaseServiceAccount {
            try {
                require(json.length in 256..16_384)
                val account = Json.parseToJsonElement(json).jsonObject
                val project = account.getValue("project_id").jsonPrimitive.content
                val email = account.getValue("client_email").jsonPrimitive.content
                val pem = account.getValue("private_key").jsonPrimitive.content
                val tokenUri = account.getValue("token_uri").jsonPrimitive.content
                require(project.matches(Regex("[a-z][a-z0-9-]{3,99}")))
                require(email.length < 256 && email.endsWith(".iam.gserviceaccount.com"))
                require(tokenUri == "https://oauth2.googleapis.com/token")
                val beginMarker = "-----BEGIN " + "PRIVATE KEY-----"
                val endMarker = "-----END " + "PRIVATE KEY-----"
                require(pem.startsWith(beginMarker) && pem.trim().endsWith(endMarker))
                val encoded =
                    pem
                        .replace(beginMarker, "")
                        .replace(endMarker, "")
                        .filterNot(Char::isWhitespace)
                val key =
                    KeyFactory.getInstance("RSA").generatePrivate(
                        PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)),
                    )
                return FirebaseServiceAccount(project, email, key)
            } catch (ex: Exception) {
                throw ConfigurationException("FCM credentials must be a valid Google service account JSON")
            }
        }
    }
}

data class PushHttpResponse(
    val status: Int,
    val body: String,
)

interface PushHttpTransport {
    suspend fun post(
        url: URI,
        headers: Map<String, String>,
        body: ByteArray,
    ): PushHttpResponse
}

class NetworkPushTransport : PushHttpTransport {
    override suspend fun post(
        url: URI,
        headers: Map<String, String>,
        body: ByteArray,
    ): PushHttpResponse =
        withContext(Dispatchers.IO) {
            require(
                url.scheme == "https" &&
                    (
                        (url.host == "oauth2.googleapis.com" && url.path == "/token") ||
                            (url.host == "fcm.googleapis.com" && url.path.matches(Regex("/v1/projects/[a-z][a-z0-9-]{3,99}/messages:send")))
                    ),
            )
            val connection = url.toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 5_000
                connection.readTimeout = 7_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                for ((name, value) in headers) connection.setRequestProperty(name, value)
                connection.outputStream.use { it.write(body) }
                val status = connection.responseCode
                // Only limited provider JSON is inspected; never include the body in an exception.
                val responseBody =
                    (if (status < 400) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(StandardCharsets.UTF_8)
                        ?.use { it.readText().take(8192) }
                        .orEmpty()
                PushHttpResponse(status, responseBody)
            } finally {
                connection.disconnect()
            }
        }
}

/**
 * Firebase HTTP v1 + OAuth2 service account JWT exchange.
 * Access tokens are cached briefly; FCM registration tokens are never recorded in logs.
 */
class FcmHttpV1Gateway(
    private val account: FirebaseServiceAccount,
    private val transport: PushHttpTransport = NetworkPushTransport(),
    private val clock: Clock = Clock.systemUTC(),
) : PushGateway {
    private val mutex = Mutex()
    private var accessToken: String? = null
    private var accessUntil: Long = 0

    override suspend fun send(
        token: String,
        title: String,
        body: String,
    ): PushDeliveryResult = send(token, title, body, emptyMap())

    override suspend fun send(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>,
    ): PushDeliveryResult {
        if (token.length !in 16..4096 ||
            token.any { it.isWhitespace() || it.isISOControl() } ||
            title.isBlank() ||
            title.length > 160 ||
            body.length > 2000 ||
            data.size > 16 ||
            data.any { (key, value) ->
                !key.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")) ||
                    value.length > 512 ||
                    value.any(Char::isISOControl)
            }
        ) {
            return PushDeliveryResult.REJECTED
        }
        val authorization =
            try {
                currentAccessToken()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return PushDeliveryResult.RETRYABLE
            }
        if (authorization == null) return PushDeliveryResult.CONFIGURATION_ERROR
        val request =
            buildJsonObject {
                put(
                    "message",
                    buildJsonObject {
                        put("token", token)
                        put(
                            "notification",
                            buildJsonObject {
                                put("title", title)
                                put("body", body)
                            },
                        )
                        if (data.isNotEmpty()) {
                            put(
                                "data",
                                buildJsonObject {
                                    data.forEach { (key, value) -> put(key, value) }
                                },
                            )
                        }
                        put(
                            "android",
                            buildJsonObject {
                                put("priority", "HIGH")
                                if (data["type"] == AttendancePushContract.TYPE) {
                                    put(
                                        "notification",
                                        buildJsonObject {
                                            put("click_action", AttendancePushContract.CLICK_ACTION)
                                            put("channel_id", AttendancePushContract.CHANNEL_ID)
                                            data[AttendancePushContract.SESSION_ID]?.let { sessionId ->
                                                put("tag", AttendancePushContract.notificationTag(sessionId))
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }.toString().toByteArray(StandardCharsets.UTF_8)
        val response =
            try {
                transport.post(
                    URI("https://fcm.googleapis.com/v1/projects/${account.projectId}/messages:send"),
                    mapOf("Authorization" to "Bearer $authorization"),
                    request,
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return PushDeliveryResult.RETRYABLE
            }
        return when {
            response.status in 200..299 -> PushDeliveryResult.ACCEPTED
            response.status in setOf(429, 500, 502, 503, 504) -> PushDeliveryResult.RETRYABLE
            response.status in setOf(401, 403) -> {
                mutex.withLock {
                    accessToken = null
                    accessUntil = 0
                }
                PushDeliveryResult.CONFIGURATION_ERROR
            }
            response.body.hasUnregisteredToken() -> PushDeliveryResult.INVALID_TOKEN
            else -> PushDeliveryResult.REJECTED
        }
    }

    private fun String.hasUnregisteredToken(): Boolean =
        runCatching {
            Json
                .parseToJsonElement(this)
                .jsonObject["error"]
                ?.jsonObject
                ?.get("details")
                ?.jsonArray
                ?.any { item ->
                    val fields = item.jsonObject
                    fields["@type"]?.jsonPrimitive?.contentOrNull == "type.googleapis.com/google.firebase.fcm.v1.FcmError" &&
                        fields["errorCode"]?.jsonPrimitive?.contentOrNull == "UNREGISTERED"
                } == true
        }.getOrDefault(false)

    private suspend fun currentAccessToken(): String? =
        mutex.withLock {
            val now = clock.instant().epochSecond
            accessToken?.takeIf { accessUntil > now + 90 }?.let { return@withLock it }
            val claim =
                buildJsonObject {
                    put("iss", account.clientEmail)
                    put("scope", "https://www.googleapis.com/auth/firebase.messaging")
                    put("aud", "https://oauth2.googleapis.com/token")
                    put("iat", now)
                    put("exp", now + 3600)
                }
            val header = """{"alg":"RS256","typ":"JWT"}"""

            fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val unsigned =
                encode(header.toByteArray(StandardCharsets.UTF_8)) + "." +
                    encode(claim.toString().toByteArray(StandardCharsets.UTF_8))
            val assertion = unsigned + "." + encode(account.sign(unsigned.toByteArray(StandardCharsets.US_ASCII)))
            val form = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + assertion
            val response =
                transport.post(
                    URI("https://oauth2.googleapis.com/token"),
                    mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    form.toByteArray(StandardCharsets.US_ASCII),
                )
            if (response.status !in 200..299) return@withLock null
            val json = runCatching { Json.parseToJsonElement(response.body).jsonObject }.getOrNull() ?: return@withLock null
            val value =
                json["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.length in 20..8192 }
                    ?: return@withLock null
            val ttl =
                json["expires_in"]?.jsonPrimitive?.longOrNull?.takeIf { it in 120..3600 }
                    ?: return@withLock null
            accessToken = value
            accessUntil = now + ttl
            value
        }
}
