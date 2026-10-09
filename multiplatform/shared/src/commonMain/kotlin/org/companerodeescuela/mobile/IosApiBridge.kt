package org.companerodeescuela.mobile

import kotlinx.coroutines.*
import org.companerodeescuela.shared.contracts.LoginResponse

/** No token or password is exposed in this result or persisted by the bridge. */
class BridgeResult(
    val success: Boolean,
    val message: String,
    val userName: String = "",
    val userId: String = "",
    val healthStatus: String = "",
    val readinessStatus: String = "",
)

class RequestHandle internal constructor(private val job: Job) {
    fun cancel() { job.cancel() }
}

/** Main-dispatcher callbacks. The owner must close this when changing API or terminating its session. */
class IosApiBridge @Throws(IllegalArgumentException::class) constructor(baseUrl: String, allowLocalHttp: Boolean) {
    private val client = MobileApiClient(ApiConfiguration(baseUrl, allowLocalHttp))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: LoginResponse? = null

    fun checkConnection(onResult: (BridgeResult) -> Unit): RequestHandle = RequestHandle(scope.launch {
        when (val health = client.health()) {
            is ApiResult.Failure -> onResult(BridgeResult(false, health.message))
            is ApiResult.Success -> {
                val ready = client.readiness()
                ensureActive()
                val readyStatus = when (ready) {
                    is ApiResult.Success -> ready.value.status
                    is ApiResult.Failure -> if (ready.statusCode == 503) "unavailable" else "unknown"
                }
                onResult(BridgeResult(
                    true,
                    if (readyStatus == "up") "API conectada y dependencias disponibles."
                    else "API conectada. Disponibilidad de dependencias: $readyStatus.",
                    healthStatus = health.value.status, readinessStatus = readyStatus,
                ))
            }
        }
    })

    fun login(username: String, password: String, onResult: (BridgeResult) -> Unit): RequestHandle =
        RequestHandle(scope.launch {
            when (val result = client.login(username, password)) {
                is ApiResult.Failure -> onResult(BridgeResult(false, result.message))
                is ApiResult.Success -> {
                    ensureActive()
                    session = result.value
                    onResult(BridgeResult(true, "Sesión iniciada. Se conserva solo mientras la app está abierta.",
                        result.value.user.displayName, result.value.user.id))
                }
            }
        })

    fun logout(onResult: (BridgeResult) -> Unit): RequestHandle {
        val previous = session
        session = null
        return RequestHandle(scope.launch {
            val result = previous?.let { client.logout(it) }
            ensureActive()
            onResult(BridgeResult(true, if (result is ApiResult.Failure)
                "Sesión local cerrada. No se pudo confirmar la revocación en el servidor."
                else "Sesión cerrada."))
        })
    }

    fun close() {
        session = null
        scope.cancel()
        client.close()
    }
}
