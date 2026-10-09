package org.companerodeescuela.core.attendance

import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
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
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.shared.contracts.AcademicWeekResponse
import org.companerodeescuela.shared.contracts.ApiError
import org.companerodeescuela.shared.contracts.ApiErrorCode
import org.companerodeescuela.shared.contracts.ApiResponse
import org.companerodeescuela.shared.contracts.AttendanceAttemptRequest
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionRequest
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionResponse
import org.companerodeescuela.shared.contracts.AttendanceQrResponse
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceRosterResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ClassCallConfirmationRequest
import org.companerodeescuela.shared.contracts.TeacherCampusRosterResponse
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest
import org.companerodeescuela.shared.contracts.SchoolPresenceResponse
import org.companerodeescuela.shared.contracts.StartSchoolPresenceRequest

class AttendanceRemoteClient @Inject constructor(
    private val client: HttpClient,
    private val refreshCoordinator: SessionRefreshCoordinator,
) {
    suspend fun currentAccessToken(): Outcome<String> = refreshCoordinator.currentAccessToken()

    suspend fun schoolPresence(
        token: String,
    ): Outcome<SchoolPresenceResponse?> = authorized(token) { accessToken ->
        apiCall {
            client.get("presence/school-day") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<SchoolPresenceResponse?>>()
        }.map { it.data }
    }

    suspend fun startSchoolPresence(
        token: String,
        request: StartSchoolPresenceRequest,
    ): Outcome<SchoolPresenceResponse> = authorized(token) { accessToken ->
        apiCall {
            client.post("presence/school-day/start") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.requireBody<ApiResponse<SchoolPresenceResponse>>()
        }.map { it.data }
    }

    suspend fun closeSchoolPresence(
        token: String,
    ): Outcome<SchoolPresenceResponse> = authorized(token) { accessToken ->
        apiCall {
            client.post("presence/school-day/close") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<SchoolPresenceResponse>>()
        }.map { it.data }
    }

    suspend fun activeStudentSessions(
        token: String,
    ): Outcome<List<AttendanceSessionResponse>> = authorized(token) { accessToken ->
        apiCall {
            client.get("attendance/sessions/active") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<List<AttendanceSessionResponse>>>()
        }.map { it.data }
    }

    suspend fun activeTeacherSessions(
        token: String,
    ): Outcome<List<AttendanceSessionResponse>> = authorized(token) { accessToken ->
        apiCall {
            client.get("attendance/sessions/mine") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<List<AttendanceSessionResponse>>>()
        }.map { it.data }
    }

    suspend fun academicWeek(
        token: String,
        weekOf: String,
    ): Outcome<AcademicWeekResponse> = authorized(token) { accessToken ->
        apiCall {
            client.get("academic/schedule/v2?weekOf=$weekOf") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<AcademicWeekResponse>>()
        }.map { it.data }
    }

    suspend fun openSession(
        token: String,
        request: CreateAttendanceSessionRequest,
    ): Outcome<AttendanceSessionResponse> = authorized(token) { accessToken ->
        apiCall {
            client.post("attendance/sessions") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.requireBody<ApiResponse<AttendanceSessionResponse>>()
        }.map { it.data }
    }

    suspend fun issueQr(
        token: String,
        sessionId: String,
    ): Outcome<AttendanceQrResponse> = authorized(token) { accessToken ->
        apiCall {
            client.post("attendance/sessions/$sessionId/qr") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<AttendanceQrResponse>>()
        }.map { it.data }
    }

    suspend fun issueQrPack(
        token: String,
        sessionId: String,
    ): Outcome<List<AttendanceQrResponse>> = authorized(token) { accessToken ->
        apiCall {
            client.post("attendance/sessions/$"+"sessionId/qr-pack") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<List<AttendanceQrResponse>>>()
        }.map { it.data }
    }

    suspend fun inspectQr(
        token: String,
        request: AttendanceQrInspectionRequest,
    ): Outcome<AttendanceQrInspectionResponse> = authorized(token) { accessToken ->
        apiCall {
            client.post("attendance/qr/inspect") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.requireBody<ApiResponse<AttendanceQrInspectionResponse>>()
        }.map { it.data }
    }

    suspend fun confirmClassCall(
        token: String, sessionId: String, request: ClassCallConfirmationRequest,
    ): Outcome<AttendanceRecordResponse> = authorized(token) { accessToken ->
        apiCall {
            client.post("attendance/sessions/$sessionId/confirm") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.requireBody<ApiResponse<AttendanceRecordResponse>>()
        }.map { it.data }
    }

    suspend fun campusRoster(
        token: String, occurrenceId: String, date: String,
    ): Outcome<TeacherCampusRosterResponse> = authorized(token) { accessToken ->
        apiCall {
            client.get("attendance/occurrences/$occurrenceId/campus-roster?date=$date") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<TeacherCampusRosterResponse>>()
        }.map { it.data }
    }

    suspend fun myClassRecord(
        token: String, sessionId: String,
    ): Outcome<AttendanceRecordResponse?> = authorized(token) { accessToken ->
        apiCall {
            client.get("attendance/sessions/$sessionId/mine") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<AttendanceRecordResponse?>>()
        }.map { it.data }
    }

    suspend fun closeSession(
        token: String,
        sessionId: String,
    ): Outcome<AttendanceSessionResponse> = authorized(token) { accessToken ->
        apiCall {
            client.post("attendance/sessions/$sessionId/close") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<AttendanceSessionResponse>>()
        }.map { it.data }
    }

    suspend fun roster(
        token: String,
        sessionId: String,
    ): Outcome<AttendanceRosterResponse> = authorized(token) { accessToken ->
        apiCall {
            client.get("attendance/sessions/$sessionId/roster") {
                bearerAuth(accessToken)
            }.requireBody<ApiResponse<AttendanceRosterResponse>>()
        }.map { it.data }
    }

    suspend fun review(
        token: String,
        recordId: String,
        request: ReviewAttendanceRequest,
    ): Outcome<AttendanceRecordResponse> = authorized(token) { accessToken ->
        apiCall {
            client.patch("attendance/records/$recordId/review") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.requireBody<ApiResponse<AttendanceRecordResponse>>()
        }.map { it.data }
    }

    suspend fun submit(
        token: String,
        operation: PendingAttendanceOperation,
    ): Outcome<AttendanceRecordResponse> = authorized(token) { accessToken ->
        apiCall {
            client.post("attendance/sessions/" + operation.sessionId + "/attempts") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    AttendanceAttemptRequest(
                        operationId = operation.operationId,
                        deviceTimestampEpochSeconds = operation.deviceTimestampEpochSeconds,
                        qrToken = operation.qrToken,
                        schoolNetwork = operation.schoolNetwork,
                    ),
                )
            }.requireBody<ApiResponse<AttendanceRecordResponse>>()
        }.map { it.data }
    }

    private suspend fun <T> authorized(
        failedAccessToken: String,
        request: suspend (String) -> Outcome<T>,
    ): Outcome<T> = refreshCoordinator.execute(failedAccessToken, request)

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
            is AppError.Storage -> "STORAGE"
            is AppError.Unknown -> "UNKNOWN"
        }
    }
}
