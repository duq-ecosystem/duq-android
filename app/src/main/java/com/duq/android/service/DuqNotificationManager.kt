package com.duq.android.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.duq.android.DuqApplication
import com.duq.android.DuqState
import com.duq.android.MainActivity
import com.duq.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DuqNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val NOTIFICATION_ID = 1
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun createNotification(state: DuqState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            context, 0,
            Intent(context, DuqListenerService::class.java).apply {
                action = DuqListenerService.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, DuqApplication.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(getNotificationText(state))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.stop_service), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun updateNotification(state: DuqState) {
        val notification = createNotification(state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getNotificationText(state: DuqState): String {
        return when (state) {
            DuqState.IDLE -> context.getString(R.string.notification_text)
            DuqState.LISTENING, DuqState.RECORDING -> context.getString(R.string.status_listening)
            DuqState.PROCESSING -> context.getString(R.string.status_processing)
            DuqState.PLAYING -> context.getString(R.string.status_playing)
            DuqState.ERROR -> context.getString(R.string.status_error)
        }
    }
}
