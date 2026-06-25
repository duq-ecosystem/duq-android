package com.duq.android.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Short-lived foreground service whose sole job is to hold the `mediaProjection`
 * FGS type while a [ScreenRecorder] capture runs. Android 14+ refuses
 * `getMediaProjection()` unless a foreground service with this exact type is live
 * at the moment of the call (there is no headless path).
 *
 * The KMP client has no long-running listener service (the WS node runs from the
 * Application), so screen.record raises this dedicated FGS for the duration of the
 * clip and tears it down right after — exactly the path the OS sanctions.
 *
 * Usage from [PhoneCommandExecutor]:
 *   1. start the service (so it is live before getMediaProjection),
 *   2. inside ScreenRecorder.record(onNeedProjectionForeground = { raise() }) the
 *      [raiseProjectionForeground] call promotes it to FGS with the mediaProjection type,
 *   3. stop the service when recording finishes.
 */
class MediaProjectionForegroundService : Service() {

    companion object {
        private const val TAG = "MediaProjFgs"
        private const val CHANNEL_ID = "duq_screen_record"
        private const val NOTIF_ID = 9101

        @Volatile var instance: MediaProjectionForegroundService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, MediaProjectionForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaProjectionForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Start as a plain (dataSync) foreground service immediately; the mediaProjection
        // type is added only once a projection token exists (raiseProjectionForeground),
        // because declaring mediaProjection without an active projection is rejected.
        startAsDataSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsDataSync()
        return START_NOT_STICKY
    }

    private fun startAsDataSync() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }.onFailure { Log.e(TAG, "startForeground(dataSync): ${it.message}") }
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    /** Promote to the mediaProjection FGS type — required by A14+ before getMediaProjection(). */
    fun raiseProjectionForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val n = buildNotification()
        runCatching {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        }.onFailure { Log.e(TAG, "raiseProjectionForeground: ${it.message}") }
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DUQ")
            .setContentText("Screen capture")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DUQ Screen Capture", NotificationManager.IMPORTANCE_MIN)
                    .apply { description = "Active while DUQ records the screen" }
            )
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
