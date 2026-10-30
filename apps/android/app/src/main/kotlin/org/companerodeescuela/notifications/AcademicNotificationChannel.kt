package org.companerodeescuela.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.companerodeescuela.BuildConfig

/** The channel is configured per flavor to isolate academic vs management notifications. */
internal object AcademicNotificationChannel {
    val channelId: String
        get() = BuildConfig.NOTIFICATION_CHANNEL_ID

    val channelName: String
        get() = BuildConfig.NOTIFICATION_CHANNEL_NAME

    fun create(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun enabled(context: Context): Boolean =
        context
            .getSystemService(NotificationManager::class.java)
            .getNotificationChannel(channelId)
            ?.importance != NotificationManager.IMPORTANCE_NONE
}
