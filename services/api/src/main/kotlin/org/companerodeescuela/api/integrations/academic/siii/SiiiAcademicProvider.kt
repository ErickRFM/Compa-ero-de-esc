package org.companerodeescuela.api.integrations.academic.siii

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import org.companerodeescuela.api.integrations.IntegrationException
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.integrations.academic.dto.ExternalAcademicLoad
import org.companerodeescuela.api.integrations.academic.dto.ExternalCourse
import org.companerodeescuela.api.integrations.academic.dto.ExternalScheduleSlot
import org.companerodeescuela.api.integrations.academic.dto.ExternalStudent
import org.companerodeescuela.api.integrations.academic.dto.ExternalTeacher
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.time.LocalDate
import java.util.Locale

/** Server-only adapter for one explicitly configured institution's Siii v1 read API. */
class SiiiAcademicProvider(
    origin: URI,
    private val token: String,
    private val timeout: Duration = Duration.ofSeconds(5),
    allowInsecureLoopback: Boolean = false,
) : AcademicProvider {
    override val id = "siii-academic-v1"
    override val displayName = "Siii institutional academic data"
    private val base: URI
    private val client: HttpClient

    init {
        require(origin.isAbsolute && !origin.host.isNullOrBlank()) { "Siii requires an absolute origin" }
        require(origin.rawUserInfo == null && origin.rawQuery == null && origin.rawFragment == null)
        require(origin.rawPath.isNullOrEmpty() || origin.rawPath == "/") { "Siii requires an origin without a path" }
        val loopback = origin.host in setOf("127.0.0.1", "localhost", "[::1]", "::1")
        require(origin.scheme == "https" || (allowInsecureLoopback && origin.scheme == "http" && loopback)) {
            "Siii requires HTTPS; explicit loopback HTTP is for local tests only"
        }
        require(TOKEN.matches(token)) { "Siii requires a base64url service credential" }
        require(timeout.toMillis() in 1..30_000) { "Siii timeout must be between 1ms and 30s" }
        base = origin.resolve("/api/integrations/companion/v1/")
        client =
            HttpClient
                .newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
    }

    override suspend fun getStudent(externalId: String): ExternalStudent? {
        val subject = subject(externalId)
        val bytes = get("students/$subject", nullableNotFound = true) ?: return null
        return decode { SiiiAcademicV1.student(bytes).also { require(it.externalId == subject) } }
    }

    override suspend fun getAcademicLoad(externalId: String): ExternalAcademicLoad {
        val subject = subject(externalId)
        val bytes = requireNotNull(get("students/$subject/academic-load"))
        return decode { SiiiAcademicV1.load(bytes).also { require(it.student.externalId == subject) } }
    }

    override suspend fun listCourses(): List<ExternalCourse> = decode { SiiiAcademicV1.courses(requireNotNull(get("courses"))) }

    override suspend fun listTeachers(): List<ExternalTeacher> = decode { SiiiAcademicV1.teachers(requireNotNull(get("teachers"))) }

    /** Siii v1 resolves student schedules and returns recurring patterns even when weekOf is supplied. */
    override suspend fun getSchedule(
        externalId: String,
        weekOf: LocalDate?,
    ): List<ExternalScheduleSlot> {
        val path = "schedules/${subject(externalId)}" + (weekOf?.let { "?weekOf=$it" } ?: "")
        val bytes = requireNotNull(get(path))
        return decode { SiiiAcademicV1.schedule(bytes) }
    }

    private fun subject(externalId: String): String {
        if (!SUBJECT.matches(externalId)) throw failure(IntegrationException.Category.BAD_REQUEST)
        return externalId.uppercase(Locale.ROOT)
    }

    private suspend fun get(
        path: String,
        nullableNotFound: Boolean = false,
    ): ByteArray? {
        val request =
            HttpRequest
                .newBuilder(base.resolve(path))
                .timeout(timeout)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .GET()
                .build()
        val response =
            try {
                withTimeoutOrNull(timeout.toMillis()) {
                    runInterruptible(Dispatchers.IO) {
                        val upstream = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
                        upstream.body().use { body ->
                            if (upstream.statusCode() != 200) return@runInterruptible upstream.statusCode() to byteArrayOf()
                            val contentType =
                                upstream
                                    .headers()
                                    .firstValue("Content-Type")
                                    .orElse("")
                                    .substringBefore(';')
                                    .trim()
                            val isJson = contentType.equals("application/json", ignoreCase = true)
                            if (!isJson) throw failure(IntegrationException.Category.MALFORMED_RESPONSE)
                            val bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1)
                            if (bytes.size > MAX_RESPONSE_BYTES) throw failure(IntegrationException.Category.MALFORMED_RESPONSE)
                            upstream.statusCode() to bytes
                        }
                    }
                } ?: throw failure(IntegrationException.Category.TIMEOUT)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: HttpTimeoutException) {
                throw failure(IntegrationException.Category.TIMEOUT)
            } catch (_: IOException) {
                throw failure(IntegrationException.Category.UNAVAILABLE)
            } catch (_: InterruptedException) {
                throw failure(IntegrationException.Category.UNAVAILABLE)
            }
        val (status, bytes) = response
        return when {
            status == 200 -> bytes
            status == 404 && nullableNotFound -> null
            status == 404 -> throw failure(IntegrationException.Category.NOT_FOUND)
            status == 401 || status == 403 -> throw failure(IntegrationException.Category.UNAUTHORIZED)
            status in 400..499 && status != 429 -> throw failure(IntegrationException.Category.BAD_REQUEST)
            else -> throw failure(IntegrationException.Category.UNAVAILABLE)
        }
    }

    private inline fun <T> decode(block: () -> T): T =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IntegrationException) {
            throw failure
        } catch (_: Exception) {
            throw failure(IntegrationException.Category.MALFORMED_RESPONSE)
        }

    private fun failure(category: IntegrationException.Category) = IntegrationException(id, category, "Siii academic request failed")

    private companion object {
        const val MAX_RESPONSE_BYTES = 1_048_576
        val SUBJECT = Regex("[A-Za-z0-9][A-Za-z0-9_-]{1,31}")
        val TOKEN = Regex("[A-Za-z0-9_-]{32,512}")
    }
}
