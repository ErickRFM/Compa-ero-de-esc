package org.companerodeescuela.core.attendance

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import javax.inject.Inject
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.PendingAttendanceOperation
import org.companerodeescuela.core.network.PlatformApiClient
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse

class AttendanceRemoteClient @Inject constructor(
    @PlatformApiClient private val client: HttpClient,
) {
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
                    ),
                )
            }.requireBody<ApiResponse<AttendanceRecordResponse>>()
        }.map { it.data }

    companion object {
        fun errorCode(error: AppError): String = when (error) {
            is AppError.Http -> "HTTP_" + error.status
            is AppError.Network -> "NETWORK"
            is AppError.Serialization -> "SERIALIZATION"
            is AppError.Unknown -> "UNKNOWN"
        }
    }
}
