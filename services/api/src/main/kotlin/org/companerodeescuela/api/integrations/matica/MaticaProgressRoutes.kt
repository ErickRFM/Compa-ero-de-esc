package org.companerodeescuela.api.integrations.matica

import com.auth0.jwt.interfaces.Payload
import com.mongodb.MongoWriteException
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.bson.Document
import org.companerodeescuela.api.auth.AuthTokenService
import org.companerodeescuela.api.auth.PlatformAccountRepository
import org.companerodeescuela.api.auth.RefreshSessionRepository
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.errors.ApiException
import org.companerodeescuela.api.security.NativeActorResolver
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.MaticaProgressLinkRequest
import org.companerodeescuela.shared.contracts.MaticaProgressSummary
import org.companerodeescuela.shared.contracts.UserRole
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private data class MaticaLinkRecord(
    val companionUserId: String,
    val maticaUserId: String,
)

private interface MaticaLinkRepository {
    suspend fun consumeAndLink(
        companionUserId: String,
        maticaUserId: String,
        nonce: String,
        expiresAt: Instant,
    ): Boolean

    suspend fun find(companionUserId: String): MaticaLinkRecord?

    suspend fun unlink(companionUserId: String)
}

private class MongoMaticaLinkRepository(
    database: MongoDatabase,
) : MaticaLinkRepository {
    private val links: MongoCollection<Document> = database.getCollection("matica_progress_links")
    private val usedCodes: MongoCollection<Document> = database.getCollection("matica_progress_link_codes")

    init {
        runBlocking {
            links.createIndex(
                Indexes.ascending("companionUserId"),
                IndexOptions().unique(true).name("ux_maticaprogress_companion"),
            )
            links.createIndex(
                Indexes.ascending("maticaUserId"),
                IndexOptions().unique(true).name("ux_maticaprogress_matica"),
            )
            usedCodes.createIndex(
                Indexes.ascending("nonce"),
                IndexOptions().unique(true).name("ux_maticaprogress_nonce"),
            )
            usedCodes.createIndex(
                Indexes.ascending("expiresAt"),
                IndexOptions().expireAfter(0, TimeUnit.SECONDS).name("ttl_maticaprogress_codes"),
            )
        }
    }

    override suspend fun consumeAndLink(
        companionUserId: String,
        maticaUserId: String,
        nonce: String,
        expiresAt: Instant,
    ): Boolean {
        try {
            usedCodes.insertOne(
                Document("nonce", nonce)
                    .append("expiresAt", Date.from(expiresAt)),
            )
        } catch (error: MongoWriteException) {
            if (error.error.code == 11000) return false
            throw error
        }

        try {
            links.insertOne(
                Document("companionUserId", companionUserId)
                    .append("maticaUserId", maticaUserId)
                    .append("linkedAt", Date.from(Instant.now())),
            )
            return true
        } catch (error: Exception) {
            // Do not burn a fresh code when the account link could not be stored.
            runCatching { usedCodes.deleteOne(eq("nonce", nonce)) }
            if (error is MongoWriteException && error.error.code == 11000) return false
            throw error
        }
    }

    override suspend fun find(companionUserId: String): MaticaLinkRecord? =
        links.find(eq("companionUserId", companionUserId)).firstOrNull()?.let { document ->
            MaticaLinkRecord(
                companionUserId = document.getString("companionUserId"),
                maticaUserId = document.getString("maticaUserId"),
            )
        }

    override suspend fun unlink(companionUserId: String) {
        links.deleteOne(eq("companionUserId", companionUserId))
    }
}

@Serializable
private data class MaticaSummaryDto(
    val totalXp: Int,
    val weeklyXp: Int,
    val level: Int,
    val streak: Int,
    val longestStreak: Int,
    val lastActiveDate: String? = null,
)

private class MaticaProgressService(
    secret: ByteArray,
    private val apiBaseUrl: String,
    private val links: MaticaLinkRepository,
) {
    private val verifier = MaticaLinkCodeVerifier(secret)
    private val json = Json { ignoreUnknownKeys = true }
    private val http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build()

    suspend fun link(
        companionUserId: String,
        code: String,
        now: Instant,
    ): Boolean {
        val claims = verifier.verify(code, now) ?: return false
        if (links.find(companionUserId)?.maticaUserId == claims.subject) return true
        return links.consumeAndLink(
            companionUserId = companionUserId,
            maticaUserId = claims.subject,
            nonce = claims.nonce,
            expiresAt = Instant.ofEpochSecond(claims.expiresAtEpochSeconds),
        )
    }

    suspend fun progress(
        companionUserId: String,
        now: Instant,
    ): MaticaProgressSummary? {
        val link = links.find(companionUserId) ?: return null
        val timestamp = now.epochSecond
        val request =
            HttpRequest.newBuilder(
                URI.create(
                    "${apiBaseUrl.trimEnd('/')}/v1/integrations/companion/summary/${link.maticaUserId}",
                ),
            )
                .timeout(Duration.ofSeconds(8))
                .header("X-Matica-Companion-Timestamp", timestamp.toString())
                .header(
                    "X-Matica-Companion-Signature",
                    verifier.summarySignature(link.maticaUserId, timestamp),
                )
                .GET()
                .build()

        val response =
            try {
                withContext(Dispatchers.IO) {
                    val upstream = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
                    val body = upstream.body().use { it.readNBytes(MAX_SUMMARY_BYTES + 1) }
                    upstream.statusCode() to body
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is InterruptedException) Thread.currentThread().interrupt()
                throw ApiException.DependencyUnavailable("El progreso de Mática no está disponible ahora")
            }

        if (response.first !in 200..299 || response.second.size > MAX_SUMMARY_BYTES) {
            throw ApiException.DependencyUnavailable("El progreso de Mática no está disponible ahora")
        }

        val summary =
            try {
                json.decodeFromString<MaticaSummaryDto>(response.second.decodeToString())
            } catch (error: Exception) {
                throw ApiException.DependencyUnavailable("El progreso de Mática no está disponible ahora")
            }

        if (
            summary.totalXp < 0 ||
            summary.weeklyXp < 0 ||
            summary.level < 1 ||
            summary.streak < 0 ||
            summary.longestStreak < summary.streak
        ) {
            throw ApiException.DependencyUnavailable("El progreso de Mática no está disponible ahora")
        }

        return MaticaProgressSummary(
            totalXp = summary.totalXp,
            weeklyXp = summary.weeklyXp,
            level = summary.level,
            streak = summary.streak,
            longestStreak = summary.longestStreak,
            lastActiveDate = summary.lastActiveDate,
        )
    }

    suspend fun unlink(companionUserId: String) {
        links.unlink(companionUserId)
    }

    private companion object {
        const val MAX_SUMMARY_BYTES = 16 * 1024
    }
}

fun Route.maticaProgressRoutes(
    settings: ApiSettings,
    accounts: PlatformAccountRepository,
    sessions: RefreshSessionRepository,
    database: MongoDatabase?,
    sharedSecret: String?,
    apiBaseUrl: String?,
) {
    if (!settings.hasAuthentication) return

    val secret =
        sharedSecret
            ?.trim()
            ?.takeIf { it.toByteArray().size >= 32 }
            ?.toByteArray()
    val baseUrl = apiBaseUrl?.trim()?.takeIf(::isSecureMaticaBaseUrl)
    val service =
        if (database != null && secret != null && baseUrl != null) {
            MaticaProgressService(secret, baseUrl, MongoMaticaLinkRepository(database))
        } else {
            null
        }
    val resolver =
        NativeActorResolver(
            accounts,
            sessions,
            AuthTokenService(settings),
            org.companerodeescuela.api.integrations.UnavailableIdentityProvider,
        )

    authenticate(AuthTokenService.PROVIDER_NAME) {
        route("/integrations/matica") {
            post("/link") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (contentLength != null && contentLength > MAX_LINK_REQUEST_BYTES) {
                    throw ApiException.Validation("El código de vínculo no es válido")
                }

                val actor = resolveStudent(call.principal<JWTPrincipal>()?.payload, resolver)
                val input = call.receive<MaticaProgressLinkRequest>()
                if (input.code.length > MAX_LINK_CODE_LENGTH) {
                    throw ApiException.Validation("El código de vínculo no es válido")
                }

                val active =
                    service
                        ?: throw ApiException.DependencyUnavailable("La conexión con Mática aún no está configurada")
                if (!active.link(actor.id, input.code, Instant.now())) {
                    throw ApiException.Conflict("El código venció, ya fue usado o no es válido")
                }
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(mapOf("linked" to true)),
                )
            }

            get("/progress") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val actor = resolveStudent(call.principal<JWTPrincipal>()?.payload, resolver)
                val active =
                    service
                        ?: throw ApiException.DependencyUnavailable("La conexión con Mática aún no está configurada")
                val summary =
                    active.progress(actor.id, Instant.now())
                        ?: throw ApiException.NotFound("Mática todavía no está vinculada")
                call.respond(HttpStatusCode.OK, ApiResponse(summary))
            }

            delete("/link") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                val actor = resolveStudent(call.principal<JWTPrincipal>()?.payload, resolver)
                val active =
                    service
                        ?: throw ApiException.DependencyUnavailable("La conexión con Mática aún no está configurada")
                active.unlink(actor.id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun isSecureMaticaBaseUrl(value: String): Boolean {
    if (value.length > 300) return false
    return try {
        val uri = URI(value)
        uri.scheme == "https" &&
            uri.host != null &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            uri.path.orEmpty() in setOf("", "/")
    } catch (_: Exception) {
        false
    }
}

private suspend fun resolveStudent(
    payload: Payload?,
    resolver: NativeActorResolver,
) =
    resolver.resolve(payload ?: throw ApiException.Unauthorized()).also { actor ->
        if (UserRole.STUDENT !in actor.roles) {
            throw ApiException.Forbidden("Una cuenta de estudiante es necesaria para vincular Mática")
        }
    }

private const val MAX_LINK_REQUEST_BYTES = 4_096
private const val MAX_LINK_CODE_LENGTH = 2_048
