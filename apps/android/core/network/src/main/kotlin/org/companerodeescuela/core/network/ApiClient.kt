package org.companerodeescuela.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Base URL of the API, per build type.
 *
 * `10.0.2.2` is the host machine as seen from the Android emulator. It is
 * deliberately not read from a resource: picking the wrong URL is a build
 * configuration error, and hiding it in an XML file only makes it harder to
 * notice in review.
 */
data class ApiEnvironment(
    val baseUrl: String,
    val name: String,
) {
    init {
        require(baseUrl.startsWith("http")) { "baseUrl must be absolute, was '$baseUrl'" }
        require(baseUrl.endsWith("/")) { "baseUrl must end with '/', was '$baseUrl'" }
    }

    companion object {
        /** Emulator talking to a backend running on the developer machine. */
        val Emulator = ApiEnvironment("http://10.0.2.2:8080/", "emulator")

        /** Physical device on the same LAN, with the machine's real address. */
        fun lan(address: String) = ApiEnvironment("http://$address:8080/", "lan")
    }
}

/** Shared JSON configuration, aligned with the API's serializer settings. */
internal val CompaneroJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Builds the API client.
 *
 * The engine is injectable so tests can drive the real request pipeline with
 * `MockEngine` instead of asserting against a hand-written fake repository.
 */
fun createApiClient(
    environment: ApiEnvironment,
    engine: HttpClientEngine? = null,
): HttpClient {
    val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false

        install(ContentNegotiation) {
            json(CompaneroJson)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }

        defaultRequest {
            url(environment.baseUrl)
            headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
    }

    return engine?.let { HttpClient(it, configure) } ?: HttpClient(Android, configure)
}
