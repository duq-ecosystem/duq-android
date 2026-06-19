package com.duq.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.duq.android.update.UpdateWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DuqApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Periodic background self-update (also runs an immediate check on cold
        // start from MainActivity). Survives app being closed.
        UpdateWorker.schedule(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            // Service channel — minimal, no sound
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DUQ Service", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Background connection service"
                    setShowBadge(false)
                }
            )
            // Messages channel — high priority for DUQ responses
            nm.createNotificationChannel(
                NotificationChannel(MESSAGES_CHANNEL_ID, "DUQ Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "DUQ responses when app is in background"
                }
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "duq_listener_channel"
        const val MESSAGES_CHANNEL_ID = "duq_messages_channel"
    }
}
