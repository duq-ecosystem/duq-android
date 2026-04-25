package com.duq.android.auth

import android.util.Log
import com.duq.android.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles OAuth token refresh operations.
 * Extracted from KeycloakAuthManager for SRP.
 */
interface TokenRefresher {
    suspend fun refreshToken(refreshToken: String): TokenRefreshResult
}

data class TokenRefreshResult(
    val success: Boolean,
    val accessToken: String = "",
    val refreshToken: String? = null,
    val expiresAt: Long = 0L,
    val error: String = ""
)

@Singleton
class KeycloakTokenRefresher @Inject constructor() : TokenRefresher {

    companion object {
        private const val TAG = "TokenRefresher"
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    override suspend fun refreshToken(refreshToken: String): TokenRefreshResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Refreshing access token via Keycloak")

            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", KeycloakConfig.CLIENT_ID)
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(KeycloakConfig.TOKEN_ENDPOINT.toString())
                .post(formBody)
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)

                val newAccessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", null)
                val expiresIn = json.optInt("expires_in", AppConfig.DEFAULT_TOKEN_EXPIRES_S)
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                Log.d(TAG, "Token refresh successful, expires in ${expiresIn}s")

                TokenRefreshResult(
                    success = true,
                    accessToken = newAccessToken,
                    refreshToken = newRefreshToken,
                    expiresAt = expiresAt
                )
            } else {
                val errorBody = response.body?.string() ?: "Token refresh failed"
                Log.e(TAG, "Token refresh failed: HTTP ${response.code}")
                TokenRefreshResult(success = false, error = errorBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh exception", e)
            TokenRefreshResult(success = false, error = e.message ?: "Token refresh failed")
        }
    }
}
