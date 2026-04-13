package com.jarvis.android.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jarvis.android.JarvisApplication
import com.jarvis.android.JarvisState
import com.jarvis.android.MainActivity
import com.jarvis.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class JarvisNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val NOTIFICATION_ID = 1
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun createNotification(state: JarvisState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            context, 0,
            Intent(context, JarvisListenerService::class.java).apply {
                action = JarvisListenerService.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, JarvisApplication.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(getNotificationText(state))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.stop_service), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun updateNotification(state: JarvisState) {
        val notification = createNotification(state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getNotificationText(state: JarvisState): String {
        return when (state) {
            JarvisState.IDLE -> context.getString(R.string.notification_text)
            JarvisState.LISTENING, JarvisState.RECORDING -> context.getString(R.string.status_listening)
            JarvisState.PROCESSING -> context.getString(R.string.status_processing)
            JarvisState.PLAYING -> context.getString(R.string.status_playing)
            JarvisState.ERROR -> context.getString(R.string.status_error)
        }
    }
}
