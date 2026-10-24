package org.companerodeescuela.feature.auth

import org.companerodeescuela.core.common.result.AppError
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginErrorResourceTest {
    @Test
    fun `known failures are never rendered as an unexplained generic error`() {
        val expected =
            mapOf(
                400 to R.string.auth_error_request,
                401 to R.string.auth_error_credentials,
                403 to R.string.auth_error_forbidden,
                404 to R.string.auth_error_endpoint,
                408 to R.string.auth_error_timeout,
                409 to R.string.auth_error_conflict,
                422 to R.string.auth_error_request,
                429 to R.string.auth_error_attempts,
                500 to R.string.auth_error_unavailable,
                502 to R.string.auth_error_unavailable,
                503 to R.string.auth_error_unavailable,
                504 to R.string.auth_error_timeout,
            )
        expected.forEach { (code, resource) ->
            assertEquals(resource, loginErrorResource(AppError.Http(code)), "HTTP $code")
        }
        assertEquals(R.string.auth_error_network, loginErrorResource(AppError.Network()))
        assertEquals(R.string.auth_error_storage, loginErrorResource(AppError.Storage()))
        assertEquals(R.string.auth_error_response, loginErrorResource(AppError.Serialization()))
    }
}
