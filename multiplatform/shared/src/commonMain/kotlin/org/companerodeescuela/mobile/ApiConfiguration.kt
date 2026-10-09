package org.companerodeescuela.mobile

import io.ktor.http.Url

class ApiConfiguration(baseUrl: String, val allowLocalHttp: Boolean = false) {
    val baseUrl: String
    init {
        require(baseUrl.isNotBlank() && baseUrl.none { it.isWhitespace() }) { "La URL de API no es válida." }
        require(Regex("^https?://[^/?#]+").containsMatchIn(baseUrl)) { "Usa una URL absoluta HTTP(S)." }
        val url = runCatching { Url(baseUrl) }.getOrElse { throw IllegalArgumentException("La URL de API no es válida.") }
        require(url.host.isNotBlank() && url.port in 1..65535) { "La URL requiere un servidor válido." }
        require(url.user == null && url.password == null && url.parameters.isEmpty() && url.fragment.isEmpty()) {
            "La URL base no debe contener credenciales, parámetros ni fragmentos."
        }
        val loopback = url.host.removeSurrounding("[", "]").lowercase() in setOf("localhost", "127.0.0.1", "::1")
        require(url.protocol.name == "https" || (allowLocalHttp && loopback && url.protocol.name == "http")) {
            "Usa HTTPS. HTTP solo está disponible para localhost en Debug."
        }
        this.baseUrl = url.toString().trimEnd('/') + "/"
    }
}
