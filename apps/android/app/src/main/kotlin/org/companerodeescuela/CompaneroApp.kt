package org.companerodeescuela

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.notifications.AcademicNotificationChannel
import org.companerodeescuela.notifications.PushRegistrationSchedule
import javax.inject.Inject

@HiltAndroidApp
class CompaneroApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var tokens: SessionTokenStore

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        AcademicNotificationChannel.create(this)
        val options =
            FirebaseOptions
                .Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .build()
        if (FirebaseApp.getApps(this).isEmpty()) FirebaseApp.initializeApp(this, options)
        applicationScope.launch {
            tokens
                .observeAccessToken()
                .map { access ->
                    SessionTokenInspector
                        .inspect(access.orEmpty())
                        ?.takeIf { SessionTokenInspector.isUsable(access) }
                        ?.let { claims -> "${claims.userId}:${claims.sessionId.orEmpty()}" }
                }.distinctUntilChanged()
                .collect { owner ->
                    if (owner != null) {
                        PushRegistrationSchedule.enqueue(this@CompaneroApp)
                    } else {
                        PushRegistrationSchedule.cancel(this@CompaneroApp)
                    }
                }
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
