package com.duq.android.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duq.android.auth.TokenRefresher
import com.duq.android.data.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically refreshes OAuth tokens.
 *
 * This prevents offline tokens from expiring due to inactivity.
 * Keycloak offlineSessionIdleTimeout is 30 days, we refresh every 3 days
 * to stay well under that limit.
 *
 * As long as device has network access periodically, biometric login works.
 */
@HiltWorker
class TokenRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val tokenRefresher: TokenRefresher
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TokenRefreshWorker"
        private const val WORK_NAME = "token_refresh_work"

        // Refresh every 3 days (well under 30-day Keycloak offline session timeout)
        private const val REFRESH_INTERVAL_DAYS = 3L

        /**
         * Schedule periodic token refresh.
         * Call this from Application.onCreate() or after successful login.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val refreshRequest = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
                REFRESH_INTERVAL_DAYS, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    refreshRequest
                )

            Log.d(TAG, "Scheduled periodic token refresh every $REFRESH_INTERVAL_DAYS days")
        }

        /**
         * Cancel periodic token refresh (e.g., on logout).
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic token refresh")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background token refresh")

        val refreshToken = settingsRepository.getRefreshToken()
        if (refreshToken.isBlank()) {
            Log.d(TAG, "No refresh token available, skipping")
            return Result.success()
        }

        val result = tokenRefresher.refreshToken(refreshToken)

        return if (result.success) {
            settingsRepository.updateAccessToken(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
                expiresAt = result.expiresAt
            )
            Log.d(TAG, "Background token refresh successful")
            Result.success()
        } else {
            Log.e(TAG, "Background token refresh failed: ${result.error}")
            // Retry later, don't fail permanently
            Result.retry()
        }
    }
}
