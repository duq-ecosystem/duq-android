package com.duq.android.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.app.NotificationCompat
import com.duq.android.logging.FileLogger

/**
 * Receives PackageInstaller session status for the self-update install.
 *
 * The important case is STATUS_PENDING_USER_ACTION: the OS needs the user to
 * confirm the install and hands back a confirmation Intent. We launch it
 * directly when possible (app foreground); if a background activity start is
 * blocked (Android 10+), we fall back to a full-screen-intent notification that
 * raises the confirm dialog — the same mechanism used for screen-record consent.
 */
class InstallResultReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AppUpdater"
        const val ACTION_INSTALL_STATUS = "com.duq.android.INSTALL_STATUS"
        const val EXTRA_VERSION = "version"
        private const val CHANNEL_ID = "duq_update_channel"
        private const val NOTIFY_ID = 9002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val flog = FileLogger(context)
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val version = intent.getIntExtra(EXTRA_VERSION, -1)
        flog.i(TAG, "InstallResultReceiver status=$status v$version")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    flog.e(TAG, "PENDING_USER_ACTION but no confirm intent")
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // The user just tapped "УСТАНОВИТЬ", so the app is foreground and a
                // direct startActivity IS allowed — pop the confirm dialog immediately
                // instead of leaving the banner looking like it reset to a button.
                val launched = runCatching { context.startActivity(confirm) }.isSuccess
                flog.i(TAG, "PENDING_USER_ACTION: direct startActivity launched=$launched")
                // Always also raise the persistent full-screen-intent notification as a
                // fallback: if the app was in the background, the direct start is
                // silently dropped by Android's background-activity-start policy.
                raiseConfirmNotification(context, confirm, version, flog)
                flog.i(TAG, "Raised install confirm notification for v$version")
            }
            PackageInstaller.STATUS_SUCCESS -> {
                flog.i(TAG, "Update installed successfully (v$version)")
                context.getSystemService(NotificationManager::class.java).cancel(NOTIFY_ID)
                context.getSharedPreferences(AppUpdater.UPDATE_PREFS, Context.MODE_PRIVATE)
                    .edit().remove(AppUpdater.KEY_AVAILABLE_VERSION).apply()
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                flog.e(TAG, "Install failed: status=$status msg=$msg (v$version)")
                // User cancelled (STATUS_FAILURE_ABORTED) or any other failure: clear
                // the ongoing confirm notification so it doesn't get stuck un-dismissable
                // in the shade. The in-app banner stays, so the user can retry from there.
                context.getSystemService(NotificationManager::class.java).cancel(NOTIFY_ID)
            }
        }
    }

    private fun raiseConfirmNotification(context: Context, confirm: Intent, version: Int, flog: FileLogger) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DUQ Updates", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "App update notifications" }
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getActivity(context, version, confirm, flags)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("DUQ Update готов")
            .setContentText("Нажми чтобы установить v$version")
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            // Persistent: stays put if the user dismisses the dialog by accident,
            // so they can tap again. Cleared on STATUS_SUCCESS. autoCancel=false +
            // ongoing so a stray swipe doesn't lose the ready-to-install update.
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(NOTIFY_ID, n)
        flog.i(TAG, "Confirm notification raised for v$version")
    }
}
