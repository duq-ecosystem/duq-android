package com.duq.android.network

import android.util.Log
import com.duq.android.auth.KeycloakConfig
import com.duq.android.config.AppConfig
import com.duq.android.data.SettingsRepository
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that automatically refreshes expired access tokens.
 *
 * When a 401 Unauthorized response is received:
 * 1. Attempts to refresh the token using the stored refresh token
 * 2. Retries the original request with the new access token
 * 3. If refresh fails, propagates the 401 to trigger re-authentication
 *
 * Thread-safe: Uses CountDownLatch to prevent concurrent refresh attempts
 * without blocking with Thread.sleep().
 *
 * IMPORTANT: Uses synchronous SettingsRepository methods to avoid runBlocking.
 */
@Singleton
class TokenRefreshInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository
) : Interceptor {

    companion object {
        private const val TAG = "TokenRefreshInterceptor"
        private const val REFRESH_WAIT_TIMEOUT_MS = 30_000L
    }

    // Prevent multiple concurrent token refreshes
    private val isRefreshing = AtomicBoolean(false)

    // Latch for waiting threads - recreated on each refresh cycle
    @Volatile
    private var refreshLatch: CountDownLatch? = null

    // Dedicated client for token refresh (no interceptors to avoid recursion)
    private val refreshClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip Keycloak requests to avoid recursion
        val keycloakHost = android.net.Uri.parse(KeycloakConfig.KEYCLOAK_URL).host
        if (originalRequest.url.host == keycloakHost) {
            return chain.proceed(originalRequest)
        }

        // PROACTIVE: Check if token is about to expire and refresh BEFORE request
        val requestWithFreshToken = ensureFreshToken(originalRequest)

        val response = chain.proceed(requestWithFreshToken)

        // REACTIVE: Handle 401 as fallback (e.g., token revoked server-side)
        if (response.code != 401) {
            return response
        }

        Log.d(TAG, "Received 401, attempting token refresh")

        // Check if another thread is already refreshing
        if (isRefreshing.compareAndSet(false, true)) {
            // We're the first - create a new latch and start refresh
            refreshLatch = CountDownLatch(1)

            try {
                return performRefreshAndRetry(chain, originalRequest, response)
            } finally {
                isRefreshing.set(false)
                refreshLatch?.countDown()
            }
        } else {
            // Another thread is refreshing - wait for it to finish
            Log.d(TAG, "Token refresh already in progress, waiting...")
            val latch = refreshLatch

            if (latch != null) {
                val completed = latch.await(REFRESH_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                if (!completed) {
                    Log.w(TAG, "Timeout waiting for token refresh")
                    return response // Return original 401
                }
            }

            return retryWithNewToken(chain, originalRequest, response)
        }
    }

    /**
     * Proactively check and refresh token BEFORE making request.
     * This prevents 401 errors by ensuring token is fresh.
     *
     * @param originalRequest The original request
     * @return Request with fresh token in Authorization header
     */
    private fun ensureFreshToken(originalRequest: Request): Request {
        val expiresAt = settingsRepository.getTokenExpiresAtSync()
        val now = System.currentTimeMillis()
        val timeUntilExpiry = expiresAt - now

        // If token expires soon, refresh it proactively
        if (timeUntilExpiry < AppConfig.TOKEN_EXPIRY_BUFFER_MS) {
            Log.d(TAG, "Token expires in ${timeUntilExpiry / 1000}s, refreshing proactively")

            // Try to acquire refresh lock
            if (isRefreshing.compareAndSet(false, true)) {
                refreshLatch = CountDownLatch(1)
                try {
                    val refreshToken = settingsRepository.getRefreshTokenSync()
                    if (refreshToken.isNotBlank()) {
                        val refreshResult = refreshAccessToken(refreshToken)
                        if (refreshResult.success) {
                            Log.d(TAG, "Proactive token refresh successful")
                            settingsRepository.updateAccessTokenSync(
                                accessToken = refreshResult.accessToken,
                                refreshToken = refreshResult.newRefreshToken,
                                expiresAt = refreshResult.expiresAt
                            )
                        } else {
                            Log.w(TAG, "Proactive token refresh failed: ${refreshResult.error}")
                        }
                    }
                } finally {
                    isRefreshing.set(false)
                    refreshLatch?.countDown()
                }
            } else {
                // Another thread is refreshing, wait for it
                Log.d(TAG, "Waiting for ongoing token refresh...")
                refreshLatch?.await(REFRESH_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        }

        // Get current (possibly refreshed) token and add to request
        val currentToken = settingsRepository.getAccessTokenSync()
        return if (currentToken.isNotBlank()) {
            originalRequest.newBuilder()
                .removeHeader("Authorization")
                .addHeader("Authorization", "Bearer $currentToken")
                .build()
        } else {
            originalRequest
        }
    }

    private fun performRefreshAndRetry(
        chain: Interceptor.Chain,
        originalRequest: Request,
        originalResponse: Response
    ): Response {
        // Use sync method - no runBlocking needed
        val refreshToken = settingsRepository.getRefreshTokenSync()

        if (refreshToken.isBlank()) {
            Log.w(TAG, "No refresh token available, cannot refresh")
            return originalResponse // Return original 401
        }

        val refreshResult = refreshAccessToken(refreshToken)

        if (refreshResult.success) {
            Log.d(TAG, "Token refresh successful, saving new tokens")

            // Use sync method - no runBlocking needed
            settingsRepository.updateAccessTokenSync(
                accessToken = refreshResult.accessToken,
                refreshToken = refreshResult.newRefreshToken,
                expiresAt = refreshResult.expiresAt
            )

            // Close the original response before retrying
            originalResponse.close()

            // Retry the original request with the new token
            val newRequest = originalRequest.newBuilder()
                .removeHeader("Authorization")
                .addHeader("Authorization", "Bearer ${refreshResult.accessToken}")
                .build()

            Log.d(TAG, "Retrying original request with new token")
            return chain.proceed(newRequest)
        } else {
            Log.e(TAG, "Token refresh failed: ${refreshResult.error}")
            // Clear tokens to force re-login
            settingsRepository.clearTokensSync()
            return originalResponse // Return original 401 to trigger re-auth
        }
    }

    private fun retryWithNewToken(
        chain: Interceptor.Chain,
        originalRequest: Request,
        originalResponse: Response
    ): Response {
        // Use sync method - no runBlocking needed
        val newToken = settingsRepository.getAccessTokenSync()

        if (newToken.isBlank()) {
            return originalResponse
        }

        originalResponse.close()

        val newRequest = originalRequest.newBuilder()
            .removeHeader("Authorization")
            .addHeader("Authorization", "Bearer $newToken")
            .build()

        return chain.proceed(newRequest)
    }

    private fun refreshAccessToken(refreshToken: String): RefreshResult {
        return try {
            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", KeycloakConfig.CLIENT_ID)
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(KeycloakConfig.TOKEN_ENDPOINT.toString())
                .post(formBody)
                .build()

            val response = refreshClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)

                RefreshResult(
                    success = true,
                    accessToken = json.getString("access_token"),
                    newRefreshToken = json.optString("refresh_token", null),
                    expiresAt = System.currentTimeMillis() +
                        (json.optInt("expires_in", AppConfig.DEFAULT_TOKEN_EXPIRES_S) * 1000L)
                )
            } else {
                val error = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Refresh failed: HTTP ${response.code} - $error")
                RefreshResult(success = false, error = error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Refresh exception", e)
            RefreshResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    private data class RefreshResult(
        val success: Boolean,
        val accessToken: String = "",
        val newRefreshToken: String? = null,
        val expiresAt: Long = 0L,
        val error: String = ""
    )
}
