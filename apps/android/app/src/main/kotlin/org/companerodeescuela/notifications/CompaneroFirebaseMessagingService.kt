package org.companerodeescuela.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.companerodeescuela.BuildConfig
import org.companerodeescuela.MainActivity
import org.companerodeescuela.R
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore

/**
 * Foreground FCM surface. Never displays arbitrary server text or PII from a
 * notification payload: all academic content remains behind the authenticated app.
 */
@AndroidEntryPoint
class CompaneroFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var sessions: SessionTokenStore

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushRegistrationSchedule.enqueue(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (!BuildConfig.FIREBASE_CONFIGURED || message.notification == null) return
        val hasSession = runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(1500) {
                SessionTokenInspector.isUsable(sessions.readAccessToken())
            }
        } == true
        if (!hasSession) return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = "companero_academic_updates"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Avisos académicos", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val activity = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(this, 0, activity,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Compañero de Clase")
            .setContentText("Tienes una nueva actualización académica.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(this).notify(
            message.messageId?.hashCode() ?: SystemClock.uptimeMillis().toInt(), notification)
    }
}
