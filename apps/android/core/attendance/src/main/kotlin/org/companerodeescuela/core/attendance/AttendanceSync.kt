package org.companerodeescuela.core.attendance

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.database.AttendanceLocalStore
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.ApiErrorCode
import org.companerodeescuela.shared.contracts.AttendanceReasonCode

interface AttendanceSyncEnqueuer {
    fun schedule()
}

@Singleton
class AttendanceSyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<AttendanceSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_WORK_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "attendance-outbox-sync"
        const val MIN_WORK_BACKOFF_SECONDS = 15L
    }
}

@HiltWorker
class AttendanceSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val localStore: AttendanceLocalStore,
    private val remoteClient: AttendanceRemoteClient,
    private val tokenStore: SessionTokenStore,
) : CoroutineWorker(appContext, params) {

    private val clock: Clock = Clock.systemUTC()

    override suspend fun doWork(): Result {
        repeat(MAX_BATCH_SIZE) {
            val operation = localStore.nextReady(clock.instant().epochSecond)
                ?: return Result.success()

            val token = runCatching { tokenStore.readAccessToken() }.getOrNull()
            val claims = token?.let(SessionTokenInspector::inspect)
            if (
                token.isNullOrBlank() ||
                claims == null ||
                !SessionTokenInspector.isUsable(token, clock) ||
                claims.userId != operation.ownerId
            ) {
                localStore.markAuthRequired(operation.operationId)
                return Result.success()
            }

            when (val result = remoteClient.submit(token, operation)) {
                is Outcome.Success -> {
                    localStore.markSynced(operation.operationId, result.value)
                }
                is Outcome.Failure -> {
                    val error = result.error
                    if (error is AppError.Http && error.status == 401) {
                        runCatching { tokenStore.clear() }
                        localStore.markAuthRequired(operation.operationId)
                        return Result.success()
                    }

                    val apiCode = AttendanceRemoteClient.apiErrorCode(error)
                    val terminalReason = when (apiCode) {
                        ApiErrorCode.ATTENDANCE_SESSION_CLOSED ->
                            AttendanceReasonCode.SESSION_CLOSED
                        ApiErrorCode.ATTENDANCE_OPERATION_CONFLICT ->
                            AttendanceReasonCode.DUPLICATE
                        ApiErrorCode.ATTENDANCE_NOT_ENROLLED ->
                            AttendanceReasonCode.NOT_ENROLLED
                        else -> null
                    }
                    if (terminalReason != null) {
                        localStore.markRejected(operation.operationId, terminalReason)
                        return@repeat
                    }

                    val retryable = when (error) {
                        is AppError.Network -> true
                        is AppError.Http -> AttendanceRetryPolicy.isRetryableHttp(error.status)
                        is AppError.Serialization,
                        is AppError.Unknown,
                        -> false
                    }
                    if (!retryable) {
                        localStore.markRejected(
                            operation.operationId,
                            AttendanceReasonCode.WRONG_SESSION,
                        )
                        return@repeat
                    }

                    val delay = AttendanceRetryPolicy.nextDelaySeconds(operation.attemptCount)
                    localStore.recordRetry(
                        operationId = operation.operationId,
                        nextAttemptAtEpochSeconds = clock.instant().epochSecond + delay,
                        errorCode = AttendanceRemoteClient.errorCode(error),
                    )
                    return Result.retry()
                }
            }
        }
        return Result.retry()
    }

    private companion object {
        const val MAX_BATCH_SIZE = 25
    }
}
