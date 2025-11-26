// Create this new file in your main package: com/unh/hoppin_android_app/NotificationHelper.kt
package com.unh.hoppin_android_app

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    @SuppressLint("MissingPermission")
    fun showSimpleNotification(
        context: Context,
        notificationId: Int,
        title: String,
        text: String
    ) {
        // Use the constant we created
        val builder = NotificationCompat.Builder(context, HOPPIN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // A default icon
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Dismiss notification when tapped

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
}