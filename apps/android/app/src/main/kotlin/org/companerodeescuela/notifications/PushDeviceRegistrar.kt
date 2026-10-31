package org.companerodeescuela.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessaging
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.companerodeescuela.BuildConfig
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.auth.AuthRepository
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ApiResponse
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** This worker never receives JWTs or FCM tokens through WorkManager data. */
object PushRegistrationSchedule {
    private const val NAME = "companero-fcm-device-register"

    fun enqueue(context: Context) {
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            PushRegistrationDiagnostics.update(PushRegistrationState.NOT_CONFIGURED)
            return
        }
        val request =
            OneTimeWorkRequestBuilder<PushTokenWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
        PushRegistrationDiagnostics.update(PushRegistrationState.NOT_REGISTERED)
    }
}

class PushDeviceRegistrar
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val http: HttpClient,
        private val store: SessionTokenStore,
        private val refresh: SessionRefreshCoordinator,
        private val auth: AuthRepository,
    ) {
        suspend fun register(fcmToken: String): PushRegistrationState {
            if (!BuildConfig.FIREBASE_CONFIGURED) return PushRegistrationState.NOT_CONFIGURED
            if (fcmToken.isBlank()) return PushRegistrationState.REJECTED
            val claims = SessionTokenInspector.inspect(store.readAccessToken().orEmpty()) ?: return PushRegistrationState.AUTH_REQUIRED
            if (claims.sessionId.isNullOrBlank()) return PushRegistrationState.AUTH_REQUIRED
            val user =
                when (val check = auth.currentUser()) {
                    is Outcome.Success -> check.value
                    is Outcome.Failure -> return pushRegistrationFailure(check.error)
                }
            if (user.id != claims.userId ||
                user.accountStatus != AccountStatus.ACTIVE ||
                !user.emailVerified
            ) {
                return PushRegistrationState.REJECTED
            }
            val prefs = context.getSharedPreferences("push_installation", Context.MODE_PRIVATE)
            val installation =
                prefs.getString("installation_id", null)?.takeIf(String::isNotBlank)
                    ?: UUID.randomUUID().toString().also { prefs.edit().putString("installation_id", it).apply() }
            val requestBody =
                buildJsonObject {
                    put("token", fcmToken)
                    put("platform", "android")
                    put(
                        "model",
                        android.os.Build.MODEL
                            .take(100),
                    )
                    put("deviceId", installation)
                    put("appDistribution", BuildConfig.APP_DISTRIBUTION)
                    put("packageName", context.packageName)
                }
            return when (val access = refresh.currentAccessToken()) {
                is Outcome.Failure -> pushRegistrationFailure(access.error)
                is Outcome.Success ->
                    when (
                        val result =
                            refresh.execute(access.value) { bearer ->
                                apiCall {
                                    http
                                        .post("devices/register") {
                                            bearerAuth(bearer)
                                            contentType(ContentType.Application.Json)
                                            setBody(requestBody)
                                        }.requireBody<ApiResponse<Boolean>>()
                                }.map { it.data }
                            }
                    ) {
                        is Outcome.Success -> if (result.value) PushRegistrationState.REGISTERED else PushRegistrationState.REJECTED
                        is Outcome.Failure -> pushRegistrationFailure(result.error)
                    }
            }
        }
    }

@HiltWorker
class PushTokenWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val registrar: PushDeviceRegistrar,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!BuildConfig.FIREBASE_CONFIGURED) {
                PushRegistrationDiagnostics.update(PushRegistrationState.NOT_CONFIGURED)
                return Result.success()
            }
            // Never fetch/register an FCM destination until notifications are enabled.
            if (Build.VERSION.SDK_INT >= 33 &&
                applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                PushRegistrationDiagnostics.update(PushRegistrationState.PERMISSION_REQUIRED)
                return Result.success()
            }
            if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled() ||
                !AcademicNotificationChannel.enabled(applicationContext)
            ) {
                PushRegistrationDiagnostics.update(PushRegistrationState.PERMISSION_REQUIRED)
                return Result.success()
            }
            PushRegistrationDiagnostics.update(PushRegistrationState.REGISTERING)
            val token =
                try {
                    FirebaseMessaging.getInstance().token.await()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    PushRegistrationDiagnostics.update(PushRegistrationState.RETRYING)
                    return Result.retry()
                }
            val state = registrar.register(token)
            PushRegistrationDiagnostics.update(state)
            return when (state) {
                PushRegistrationState.REGISTERED, PushRegistrationState.NOT_CONFIGURED -> Result.success()
                PushRegistrationState.RETRYING -> Result.retry()
                else -> Result.failure()
            }
        }
    }
