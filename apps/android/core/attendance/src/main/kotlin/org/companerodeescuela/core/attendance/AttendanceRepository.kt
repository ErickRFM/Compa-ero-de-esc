package org.companerodeescuela.core.attendance

import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AttendanceLocalStore
import org.companerodeescuela.core.database.LocalAttendanceRecord
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AttendanceSessionResponse

class AttendanceRepository(
    private val tokenStore: SessionTokenStore,
    private val localStore: AttendanceLocalStore,
    private val scheduler: AttendanceSyncEnqueuer,
    private val remoteClient: AttendanceRemoteClient,
    private val clock: Clock = Clock.systemUTC(),
    private val newOperationId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun loadActiveSessions(): Outcome<List<AttendanceSessionResponse>> {
        val token = runCatching { tokenStore.readAccessToken() }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return Outcome.Failure(AppError.Http(status = 401))
        if (!SessionTokenInspector.isUsable(token, clock)) {
            runCatching { tokenStore.clear() }
            return Outcome.Failure(AppError.Http(status = 401))
        }
        return when (val result = remoteClient.activeSessions(token)) {
            is Outcome.Success -> result
            is Outcome.Failure -> {
                if ((result.error as? AppError.Http)?.status == 401) {
                    runCatching { tokenStore.clear() }
                }
                result
            }
        }
    }

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

        val token = runCatching { tokenStore.readAccessToken() }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return Outcome.Failure(AppError.Http(status = 401))
        if (!SessionTokenInspector.isUsable(token, clock)) {
            runCatching { tokenStore.clear() }
            return Outcome.Failure(AppError.Http(status = 401))
        }
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
        val token = runCatching { tokenStore.readAccessToken() }.getOrNull()
        if (SessionTokenInspector.isUsable(token, clock)) {
            scheduler.schedule()
        }
    }

    fun observe(ownerId: String): Flow<List<LocalAttendanceRecord>> =
        localStore.observe(ownerId)

    private companion object {
        const val MAX_QR_TOKEN_LENGTH = 2_048
    }
}
