package com.duq.android.network

import android.util.Log
import com.duq.android.auth.KeycloakConfig
import com.duq.android.config.AppConfig
import com.duq.android.data.SettingsRepository
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
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
 * Thread-safe: Uses AtomicBoolean to prevent concurrent refresh attempts.
 */
@Singleton
class TokenRefreshInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository
) : Interceptor {

    companion object {
        private const val TAG = "TokenRefreshInterceptor"
    }

    // Prevent multiple concurrent token refreshes
    private val isRefreshing = AtomicBoolean(false)

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
        val response = chain.proceed(originalRequest)

        // Only handle 401 for API requests (not Keycloak requests)
        val keycloakHost = android.net.Uri.parse(KeycloakConfig.KEYCLOAK_URL).host
        if (response.code != 401 || originalRequest.url.host == keycloakHost) {
            return response
        }

        Log.d(TAG, "Received 401, attempting token refresh")

        // Attempt token refresh
        synchronized(this) {
            // Double-check: another thread might have refreshed while we waited
            if (isRefreshing.get()) {
                Log.d(TAG, "Token refresh already in progress, waiting...")
                // Wait for the other thread to finish, then retry with potentially new token
                while (isRefreshing.get()) {
                    Thread.sleep(100)
                }
                return retryWithNewToken(chain, originalRequest, response)
            }

            isRefreshing.set(true)
        }

        try {
            val refreshToken = runBlocking { settingsRepository.getRefreshToken() }

            if (refreshToken.isBlank()) {
                Log.w(TAG, "No refresh token available, cannot refresh")
                isRefreshing.set(false)
                return response // Return original 401
            }

            val refreshResult = refreshAccessToken(refreshToken)

            if (refreshResult.success) {
                Log.d(TAG, "Token refresh successful, saving new tokens")

                // Save new tokens
                runBlocking {
                    settingsRepository.updateAccessToken(
                        accessToken = refreshResult.accessToken,
                        refreshToken = refreshResult.newRefreshToken,
                        expiresAt = refreshResult.expiresAt
                    )
                }

                // Close the original response before retrying
                response.close()

                // Retry the original request with the new token
                val newRequest = originalRequest.newBuilder()
                    .removeHeader("Authorization")
                    .addHeader("Authorization", "Bearer ${refreshResult.accessToken}")
                    .build()

                Log.d(TAG, "Retrying original request with new token")
                return chain.proceed(newRequest)
            } else {
                Log.e(TAG, "Token refresh failed: ${refreshResult.error}")
                return response // Return original 401 to trigger re-auth
            }
        } finally {
            isRefreshing.set(false)
        }
    }

    private fun retryWithNewToken(
        chain: Interceptor.Chain,
        originalRequest: Request,
        originalResponse: Response
    ): Response {
        val newToken = runBlocking { settingsRepository.getAccessToken() }

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
