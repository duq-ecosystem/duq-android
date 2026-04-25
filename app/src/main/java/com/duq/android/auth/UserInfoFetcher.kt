package com.duq.android.auth

import android.util.Log
import com.duq.android.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User info from OAuth provider
 */
data class UserInfo(
    val sub: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String,
    val preferredUsername: String,
    val givenName: String,
    val familyName: String
)

/**
 * Fetches user information from OAuth provider.
 * Extracted from KeycloakAuthManager for SRP.
 */
interface UserInfoFetcher {
    suspend fun getUserInfo(accessToken: String): Result<UserInfo>
}

@Singleton
class KeycloakUserInfoFetcher @Inject constructor() : UserInfoFetcher {

    companion object {
        private const val TAG = "UserInfoFetcher"
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    override suspend fun getUserInfo(accessToken: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(KeycloakConfig.USERINFO_ENDPOINT.toString())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val json = JSONObject(responseBody)

                val userInfo = UserInfo(
                    sub = json.optString("sub"),
                    email = json.optString("email"),
                    emailVerified = json.optBoolean("email_verified", false),
                    name = json.optString("name"),
                    preferredUsername = json.optString("preferred_username"),
                    givenName = json.optString("given_name"),
                    familyName = json.optString("family_name")
                )

                Log.d(TAG, "User info retrieved successfully")
                Result.success(userInfo)
            } else {
                val error = response.body?.string() ?: "Failed to get user info"
                Log.e(TAG, "User info error: ${response.code}")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "User info exception", e)
            Result.failure(e)
        }
    }
}
