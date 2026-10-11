package org.companerodeescuela.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.google.firebase.messaging.FirebaseMessaging
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.http.contentType
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.companerodeescuela.BuildConfig
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.core.common.result.Outcome
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.network.apiCall
import org.companerodeescuela.core.network.requireBody
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.auth.AuthRepository
import org.companerodeescuela.shared.contracts.AccountStatus
import org.companerodeescuela.shared.contracts.ApiResponse

/** This worker never receives JWTs or FCM tokens through WorkManager data. */
object PushRegistrationSchedule {
    private const val NAME = "companero-fcm-device-register"
    fun enqueue(context: Context) {
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        val request = OneTimeWorkRequestBuilder<PushTokenWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
    }
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}

class PushDeviceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: HttpClient,
    private val store: SessionTokenStore,
    private val refresh: SessionRefreshCoordinator,
    private val auth: AuthRepository,
) {
    suspend fun register(fcmToken: String): Boolean {
        if (!BuildConfig.FIREBASE_CONFIGURED || fcmToken.isBlank()) return true
        val claims = SessionTokenInspector.inspect(store.readAccessToken().orEmpty()) ?: return true
        if (claims.sessionId.isNullOrBlank()) return true
        val user = when (val check = auth.currentUser()) {
            is Outcome.Success -> check.value
            is Outcome.Failure -> {
                // An expired/forbidden account must not be registered; server retries are not useful.
                val status = (check.error as? AppError.Http)?.status
                return status == 401 || status == 403
            }
        }
        if (user.id != claims.userId || user.accountStatus != AccountStatus.ACTIVE || !user.emailVerified) return true
        val prefs = context.getSharedPreferences("push_installation", Context.MODE_PRIVATE)
        val installation = prefs.getString("installation_id", null)?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("installation_id", it).apply() }
        val requestBody = buildJsonObject {
            put("token", fcmToken)
            put("platform", "android")
            put("model", android.os.Build.MODEL.take(100))
            put("deviceId", installation)
        }
        return when (val access = refresh.currentAccessToken()) {
            is Outcome.Failure -> (access.error as? AppError.Http)?.status == 401
            is Outcome.Success -> when (val result = refresh.execute(access.value) { bearer ->
                apiCall {
                    http.post("devices/register") {
                        bearerAuth(bearer)
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }.requireBody<ApiResponse<Boolean>>()
                }.map { it.data }
            }) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> (result.error as? AppError.Http)?.status in listOf(400, 401, 403)
            }
        }
    }
}

@HiltWorker
class PushTokenWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val registrar: PushDeviceRegistrar,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.FIREBASE_CONFIGURED) return Result.success()
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.retry()
        }
        return if (registrar.register(token)) Result.success() else Result.retry()
    }
}
