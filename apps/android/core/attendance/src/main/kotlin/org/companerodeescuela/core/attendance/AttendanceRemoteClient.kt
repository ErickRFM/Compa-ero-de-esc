package org.companerodeescuela.core.attendance

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import javax.inject.Inject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.PendingAttendanceOperation
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.shared.contracts.ApiError
import org.companerodeescuela.shared.contracts.ApiErrorCode
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse

interface AttendanceSessionSource {
    suspend fun activeSessions(token: String): Outcome<List<AttendanceSessionResponse>>
}

class AttendanceRemoteClient @Inject constructor(
    private val client: HttpClient,
) : AttendanceSessionSource {
    override suspend fun activeSessions(token: String): Outcome<List<AttendanceSessionResponse>> =
        apiCall {
            client.get("attendance/sessions/active") {
                bearerAuth(token)
            }.requireBody<ApiResponse<List<AttendanceSessionResponse>>>()
        }.map { it.data }

    suspend fun submit(
        token: String,
        operation: PendingAttendanceOperation,
    ): Outcome<AttendanceRecordResponse> =
        apiCall {
            client.post("attendance/sessions/" + operation.sessionId + "/attempts") {
                bearerAuth(token)
                setBody(
                    AttendanceAttemptRequest(
                        operationId = operation.operationId,
                        deviceTimestampEpochSeconds = operation.deviceTimestampEpochSeconds,
                        qrToken = operation.qrToken,
                    ),
                )
            }.requireBody<ApiResponse<AttendanceRecordResponse>>()
        }.map { it.data }

    companion object {
        private val errorJson = Json { ignoreUnknownKeys = true }

        fun apiErrorCode(error: AppError): ApiErrorCode? =
            (error as? AppError.Http)
                ?.technicalDetail
                ?.let { raw -> runCatching { errorJson.decodeFromString<ApiError>(raw).code }.getOrNull() }

        fun errorCode(error: AppError): String = when (error) {
            is AppError.Http -> "HTTP_" + error.status
            is AppError.Network -> "NETWORK"
            is AppError.Serialization -> "SERIALIZATION"
            is AppError.Unknown -> "UNKNOWN"
        }
    }
}
