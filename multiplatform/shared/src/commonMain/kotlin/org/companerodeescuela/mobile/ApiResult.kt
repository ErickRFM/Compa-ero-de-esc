package org.companerodeescuela.mobile

enum class FailureKind { HTTP, NETWORK, FORMAT }

sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    data class Failure(
        val kind: FailureKind,
        val message: String,
        val statusCode: Int = 0,
    ) : ApiResult<Nothing>()
}
