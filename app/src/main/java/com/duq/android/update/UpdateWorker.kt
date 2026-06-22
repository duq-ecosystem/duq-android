package com.duq.android.update

import android.app.NotificationManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duq.android.logging.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Periodic background self-update check — so the app updates itself without the
 * user having to open it first. Runs every [INTERVAL_HOURS] hours when network
 * is available; AppUpdater downloads any newer APK and raises the OS install
 * confirm dialog via the PackageInstaller session callback.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val flog = FileLogger(applicationContext)
        flog.i("AppUpdater", "UpdateWorker.doWork() started")
        try {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            // Background: only detect + notify (no 33MB download on a timer/battery).
            AppUpdater(applicationContext, nm.areNotificationsEnabled()).checkAvailable()
            // Заодно проверяем обновление ЯДРА DUQ → пуш с deep-link в «Движок».
            CoreUpdateNotifier.check(applicationContext)
            flog.i("AppUpdater", "UpdateWorker.doWork() finished")
            Result.success()
        } catch (e: Exception) {
            flog.e("AppUpdater", "UpdateWorker failed: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "duq-self-update"
        private const val INTERVAL_HOURS = 6L

        /** Schedules the recurring check; KEEP so re-launches don't reset the timer. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
