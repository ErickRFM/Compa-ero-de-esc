package org.companerodeescuela.core.attendance

import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AttendanceLocalStore
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.security.PlatformSessionClaims
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AcademicWeekResponse
import org.companerodeescuela.shared.contracts.AttendanceQrInspectionRequest\nimport org.companerodeescuela.shared.contracts.AttendanceQrInspectionResponse\nimport org.companerodeescuela.shared.contracts.AttendanceQrResponse
import org.companerodeescuela.shared.contracts.AttendanceRecordResponse
import org.companerodeescuela.shared.contracts.AttendanceRosterResponse
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse
import org.companerodeescuela.shared.contracts.CreateAttendanceSessionRequest
import org.companerodeescuela.shared.contracts.ReviewAttendanceRequest

class AttendanceRepository(
    private val tokenStore: SessionTokenStore,
    private val localStore: AttendanceLocalStore,
    private val scheduler: AttendanceSyncEnqueuer,
    private val remoteClient: AttendanceRemoteClient,
    private val clock: Clock = Clock.systemUTC(),
    private val newOperationId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun localSessionClaims(): Outcome<PlatformSessionClaims> {
        val token = currentToken().valueOrNull()
            ?: return Outcome.Failure(AppError.Http(status = 401))
        val claims = SessionTokenInspector.inspect(token)
            ?: return Outcome.Failure(AppError.Http(status = 401))
        return Outcome.Success(claims)
    }

    suspend fun activeStudentSessions(): Outcome<List<AttendanceSessionResponse>> =
        withToken(remoteClient::activeStudentSessions)

    suspend fun activeTeacherSessions(): Outcome<List<AttendanceSessionResponse>> =
        withToken(remoteClient::activeTeacherSessions)

    suspend fun academicWeek(weekOf: String): Outcome<AcademicWeekResponse> =
        withToken { token -> remoteClient.academicWeek(token, weekOf) }

    suspend fun openSession(
        request: CreateAttendanceSessionRequest,
    ): Outcome<AttendanceSessionResponse> =
        withToken { token -> remoteClient.openSession(token, request) }

    suspend fun issueQr(sessionId: String): Outcome<AttendanceQrResponse> =
        withToken { token -> remoteClient.issueQr(token, sessionId) }

    suspend fun inspectQr(
        request: AttendanceQrInspectionRequest,
    ): Outcome<AttendanceQrInspectionResponse> =
        withToken { token -> remoteClient.inspectQr(token, request) }

    suspend fun closeSession(sessionId: String): Outcome<AttendanceSessionResponse> =
        withToken { token -> remoteClient.closeSession(token, sessionId) }

    suspend fun roster(sessionId: String): Outcome<AttendanceRosterResponse> =
        withToken { token -> remoteClient.roster(token, sessionId) }

    suspend fun review(
        recordId: String,
        request: ReviewAttendanceRequest,
    ): Outcome<AttendanceRecordResponse> =
        withToken { token -> remoteClient.review(token, recordId, request) }

    suspend fun enqueueAttempt(
        sessionId: String,
        qrToken: String? = null,
    ): Outcome<LocalAttendanceRecord> {
        if (sessionId.isBlank()) {
            return Outcome.Failure(
                AppError.Serialization("Attendance session id was blank"),
            )
        }

        val normalizedQrToken = qrToken?.trim()?.takeIf(String::isNotEmpty)
        if (normalizedQrToken != null && normalizedQrToken.length > MAX_QR_TOKEN_LENGTH) {
            return Outcome.Failure(
                AppError.Serialization("Attendance QR token was too long"),
            )
        }

        val token = currentToken().valueOrNull()
            ?: return Outcome.Failure(AppError.Http(status = 401))
        val claims = SessionTokenInspector.inspect(token)
            ?: return Outcome.Failure(AppError.Http(status = 401))

        return try {
            val local = localStore.enqueue(
                operationId = newOperationId(),
                ownerId = claims.userId,
                sessionId = sessionId,
                deviceTimestampEpochSeconds = clock.instant().epochSecond,
                qrToken = normalizedQrToken,
            )
            scheduler.schedule()
            Outcome.Success(local)
        } catch (error: Exception) {
            Outcome.Failure(
                AppError.Unknown(
                    technicalDetail = "Could not queue attendance: " + error::class.simpleName,
                ),
            )
        }
    }

    suspend fun resumePending() {
        if (currentToken() is Outcome.Success) {
            scheduler.schedule()
        }
    }

    fun observe(ownerId: String): Flow<List<LocalAttendanceRecord>> =
        localStore.observe(ownerId)

    private suspend fun currentToken(): Outcome<String> {
        val token = runCatching { tokenStore.readAccessToken() }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return Outcome.Failure(AppError.Http(status = 401))

        if (!SessionTokenInspector.isUsable(token, clock)) {
            runCatching { tokenStore.clear() }
            return Outcome.Failure(AppError.Http(status = 401))
        }
        return Outcome.Success(token)
    }

    private suspend fun <T> withToken(
        block: suspend (String) -> Outcome<T>,
    ): Outcome<T> {
        return when (val token = currentToken()) {
            is Outcome.Success -> block(token.value)
            is Outcome.Failure -> token
        }
    }

    private companion object {
        const val MAX_QR_TOKEN_LENGTH = 2_048
    }
}
