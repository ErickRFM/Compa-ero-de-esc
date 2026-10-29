package org.companerodeescuela.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.companerodeescuela.BuildConfig
import org.companerodeescuela.MainActivity
import org.companerodeescuela.R
import org.companerodeescuela.core.security.SessionTokenInspector
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.shared.contracts.AttendancePushContract
import java.util.UUID
import javax.inject.Inject

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
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        val sessionId =
            message.data[AttendancePushContract.SESSION_ID]
                ?.let { raw ->
                    runCatching { UUID.fromString(raw).toString() }
                        .getOrNull()
                        ?.takeIf { it.equals(raw, ignoreCase = true) }
                }
        val attendanceNotice = message.data["type"] == AttendancePushContract.TYPE && sessionId != null
        if (message.notification == null && !attendanceNotice) return
        val hasSession =
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(1500) {
                    SessionTokenInspector.isUsable(sessions.readAccessToken())
                }
            } == true
        if (!hasSession) return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        AcademicNotificationChannel.create(this)
        val channelId = AttendancePushContract.CHANNEL_ID
        val activity =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (attendanceNotice) {
                    action = AttendancePushContract.CLICK_ACTION
                    data = Uri.parse("companero://attendance/$sessionId")
                    putExtra(AttendancePushContract.SESSION_ID, sessionId)
                }
            }
        val pending =
            PendingIntent.getActivity(
                this,
                sessionId?.hashCode() ?: 0,
                activity,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val contentText =
            if (attendanceNotice) {
                "Tu docente abrió el pase de lista. Toca para revisarlo."
            } else {
                "Tienes una nueva actualización académica."
            }
        val notification =
            NotificationCompat
                .Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Compañero de Clase")
                .setContentText(contentText)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
        val notifications = NotificationManagerCompat.from(this)
        if (attendanceNotice && sessionId != null) {
            notifications.notify(AttendancePushContract.notificationTag(sessionId), 0, notification)
        } else {
            notifications.notify(message.messageId?.hashCode() ?: SystemClock.uptimeMillis().toInt(), notification)
        }
    }
}
