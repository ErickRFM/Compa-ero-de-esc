package org.companerodeescuela.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.companerodeescuela.shared.contracts.AttendancePushContract

/** The same channel is used by Firebase's background renderer and our foreground renderer. */
internal object AcademicNotificationChannel {
    fun create(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(AttendancePushContract.CHANNEL_ID, "Avisos académicos", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun enabled(context: Context): Boolean =
        context
            .getSystemService(NotificationManager::class.java)
            .getNotificationChannel(AttendancePushContract.CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
}
