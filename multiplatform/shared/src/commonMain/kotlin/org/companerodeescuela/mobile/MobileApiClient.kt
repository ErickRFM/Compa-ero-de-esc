package org.companerodeescuela.mobile

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.companerodeescuela.shared.contracts.*

internal expect fun createPlatformEngine(): HttpClientEngine

class MobileApiClient(
    configuration: ApiConfiguration,
    private val engine: HttpClientEngine = createPlatformEngine(),
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        defaultRequest {
            url(configuration.baseUrl)
            accept(ContentType.Application.Json)
        }
    }

    suspend fun health(): ApiResult<HealthPayload> = request { client.get("health") }
    suspend fun readiness(): ApiResult<ReadinessPayload> = request { client.get("ready") }

    suspend fun login(username: String, password: String): ApiResult<LoginResponse> {
        val result = request<ApiResponse<LoginResponse>> {
            client.post("auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username.trim(), password))
            }
        }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.value.data)
            is ApiResult.Failure -> result
        }
    }

    suspend fun logout(session: LoginResponse): ApiResult<Unit> = try {
        val response = client.post("auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(RefreshSessionRequest(session.sessionId, session.refreshToken))
        }
        if (response.status.value in 200..299) ApiResult.Success(Unit) else httpFailure(response.status.value)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ApiResult.Failure(FailureKind.NETWORK, "No se pudo conectar con la API.")
    }

    private suspend inline fun <reified T> request(block: () -> HttpResponse): ApiResult<T> = try {
        val response = block()
        if (response.status.value !in 200..299) {
            httpFailure(response.status.value)
        } else {
            try { ApiResult.Success(response.body<T>()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ApiResult.Failure(FailureKind.FORMAT, "La API devolvió una respuesta incompatible.") }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ApiResult.Failure(FailureKind.NETWORK, "No se pudo conectar con la API.")
    }

    private fun httpFailure(status: Int) = ApiResult.Failure(FailureKind.HTTP, when (status) {
        401 -> "La sesión o las credenciales no son válidas."
        403 -> "Tu cuenta no tiene permiso para esta operación."
        429 -> "Demasiados intentos. Intenta más tarde."
        503 -> "La API está disponible, pero una dependencia no está lista."
        else -> "La API no pudo completar la operación (HTTP $status)."
    }, status)

    fun close() { client.close(); engine.close() }
}
