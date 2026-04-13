package com.jarvis.android.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.jarvis.android.config.AppConfig
import com.jarvis.android.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Keycloak OIDC authentication using AppAuth library
 */
@Singleton
class KeycloakAuthManager @Inject constructor(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "KeycloakAuthManager"
        private const val PREFS_NAME = "keycloak_auth"
        private const val KEY_CODE_VERIFIER = "code_verifier"
    }

    // CoroutineScope for async callbacks - prevents runBlocking ANR issues
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val authService: AuthorizationService by lazy {
        AuthorizationService(context)
    }

    private val serviceConfig: AuthorizationServiceConfiguration by lazy {
        AuthorizationServiceConfiguration(
            KeycloakConfig.AUTHORIZATION_ENDPOINT,
            KeycloakConfig.TOKEN_ENDPOINT,
            null, // registration endpoint
            KeycloakConfig.END_SESSION_ENDPOINT
        )
    }

    /**
     * Creates an authorization request for Keycloak login with PKCE
     */
    fun createAuthorizationRequest(): AuthorizationRequest {
        // AppAuth auto-generates PKCE code_verifier when not explicitly set
        return AuthorizationRequest.Builder(
            serviceConfig,
            KeycloakConfig.CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(KeycloakConfig.REDIRECT_URI)
        )
            .setScopes(KeycloakConfig.SCOPES)
            .setPrompt("login") // Always show login screen
            // Don't call setCodeVerifier - AppAuth will generate PKCE automatically
            .build()
    }

    /**
     * Creates an intent for launching the authorization flow
     * Stores the code_verifier for later token exchange
     */
    fun getAuthorizationIntent(): Intent {
        val authRequest = createAuthorizationRequest()

        // Store the code verifier for later use in token exchange
        authRequest.codeVerifier?.let { verifier ->
            prefs.edit().putString(KEY_CODE_VERIFIER, verifier).apply()
            Log.d(TAG, "Stored code verifier: ${verifier.take(10)}...")
        }

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Gets the stored code verifier
     */
    fun getStoredCodeVerifier(): String? {
        return prefs.getString(KEY_CODE_VERIFIER, null)
    }

    /**
     * Clears the stored code verifier
     */
    fun clearCodeVerifier() {
        prefs.edit().remove(KEY_CODE_VERIFIER).apply()
    }

    /**
     * Handles the authorization response and exchanges code for tokens
     */
    suspend fun handleAuthorizationResponse(
        intent: Intent,
        onSuccess: suspend (accessToken: String, refreshToken: String?, idToken: String?, expiresAt: Long) -> Unit,
        onError: (String) -> Unit
    ) {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        when {
            response != null -> {
                Log.d(TAG, "Authorization successful, exchanging code for tokens")
                exchangeCodeForTokens(response, onSuccess, onError)
            }
            exception != null -> {
                Log.e(TAG, "Authorization error: ${exception.error} - ${exception.errorDescription}")
                onError(exception.errorDescription ?: exception.error ?: "Authorization failed")
            }
            else -> {
                Log.e(TAG, "No response or exception in intent")
                onError("Authorization was cancelled")
            }
        }
    }

    /**
     * Exchanges authorization code for tokens
     */
    private suspend fun exchangeCodeForTokens(
        response: AuthorizationResponse,
        onSuccess: suspend (accessToken: String, refreshToken: String?, idToken: String?, expiresAt: Long) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val tokenRequest = response.createTokenExchangeRequest()

            authService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                when {
                    tokenResponse != null -> {
                        Log.d(TAG, "Token exchange successful")
                        Log.d(TAG, "Access token: ${tokenResponse.accessToken?.take(20)}...")
                        Log.d(TAG, "Refresh token present: ${tokenResponse.refreshToken != null}")
                        Log.d(TAG, "ID token present: ${tokenResponse.idToken != null}")
                        Log.d(TAG, "Expires at: ${tokenResponse.accessTokenExpirationTime}")

                        // Use scope.launch instead of runBlocking to avoid ANR
                        scope.launch {
                            onSuccess(
                                tokenResponse.accessToken ?: "",
                                tokenResponse.refreshToken,
                                tokenResponse.idToken,
                                tokenResponse.accessTokenExpirationTime ?: 0L
                            )
                        }
                    }
                    exception != null -> {
                        Log.e(TAG, "Token exchange error: ${exception.error} - ${exception.errorDescription}")
                        onError(exception.errorDescription ?: exception.error ?: "Token exchange failed")
                    }
                    else -> {
                        onError("Unknown token exchange error")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange exception", e)
            onError(e.message ?: "Token exchange failed")
        }
    }

    /**
     * Refreshes the access token using the refresh token
     */
    suspend fun refreshAccessToken(
        refreshToken: String,
        onSuccess: suspend (accessToken: String, newRefreshToken: String?, expiresAt: Long) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Refreshing access token")

            val tokenRequest = TokenRequest.Builder(
                serviceConfig,
                KeycloakConfig.CLIENT_ID
            )
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setRefreshToken(refreshToken)
                .build()

            authService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                when {
                    tokenResponse != null -> {
                        Log.d(TAG, "Token refresh successful")
                        // Use scope.launch instead of runBlocking to avoid ANR
                        scope.launch {
                            onSuccess(
                                tokenResponse.accessToken ?: "",
                                tokenResponse.refreshToken,
                                tokenResponse.accessTokenExpirationTime ?: 0L
                            )
                        }
                    }
                    exception != null -> {
                        Log.e(TAG, "Token refresh error: ${exception.error}")
                        onError(exception.errorDescription ?: "Token refresh failed")
                    }
                    else -> {
                        onError("Unknown token refresh error")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh exception", e)
            onError(e.message ?: "Token refresh failed")
        }
    }

    /**
     * Token response data class
     */
    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String?,
        val idToken: String?,
        val expiresAt: Long
    )

    /**
     * Exchanges authorization code for tokens manually (with PKCE support)
     */
    suspend fun exchangeCodeForTokens(code: String): Result<TokenResponse> = withContext(Dispatchers.IO) {
        try {
            val codeVerifier = getStoredCodeVerifier()
            if (codeVerifier == null) {
                Log.e(TAG, "No code verifier found - PKCE will fail")
                return@withContext Result.failure(Exception("No code verifier found"))
            }

            val url = URL(KeycloakConfig.TOKEN_ENDPOINT.toString())
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            connection.connectTimeout = AppConfig.AUTH_TIMEOUT_MS.toInt()
            connection.readTimeout = AppConfig.AUTH_TIMEOUT_MS.toInt()

            // Build form data with PKCE code_verifier
            val formData = buildString {
                append("grant_type=authorization_code")
                append("&client_id=${KeycloakConfig.CLIENT_ID}")
                append("&code=$code")
                append("&redirect_uri=${java.net.URLEncoder.encode(KeycloakConfig.REDIRECT_URI, "UTF-8")}")
                append("&code_verifier=$codeVerifier")
            }

            Log.d(TAG, "Token exchange request to: $url")
            Log.d(TAG, "Using code verifier: ${codeVerifier.take(10)}...")
            connection.outputStream.bufferedWriter().use { it.write(formData) }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", null)
                val idToken = json.optString("id_token", null)
                val expiresIn = json.optLong("expires_in", AppConfig.DEFAULT_TOKEN_EXPIRES_S.toLong())
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

                Log.d(TAG, "Token exchange successful!")
                Log.d(TAG, "Access token: ${accessToken.take(20)}...")
                Log.d(TAG, "Refresh token present: ${refreshToken != null}")
                Log.d(TAG, "ID token present: ${idToken != null}")

                // Clear the stored code verifier after successful exchange
                clearCodeVerifier()

                Result.success(TokenResponse(accessToken, refreshToken, idToken, expiresAt))
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Token exchange failed"
                Log.e(TAG, "Token exchange error: ${connection.responseCode} - $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange exception", e)
            Result.failure(e)
        }
    }

    /**
     * Gets user info from Keycloak
     */
    suspend fun getUserInfo(accessToken: String): Result<UserInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(KeycloakConfig.USERINFO_ENDPOINT.toString())
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.connectTimeout = AppConfig.AUTH_TIMEOUT_MS.toInt()
            connection.readTimeout = AppConfig.AUTH_TIMEOUT_MS.toInt()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                val userInfo = UserInfo(
                    sub = json.optString("sub"),
                    email = json.optString("email"),
                    emailVerified = json.optBoolean("email_verified", false),
                    name = json.optString("name"),
                    preferredUsername = json.optString("preferred_username"),
                    givenName = json.optString("given_name"),
                    familyName = json.optString("family_name")
                )

                Log.d(TAG, "User info: ${userInfo.preferredUsername}")
                Result.success(userInfo)
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Failed to get user info"
                Log.e(TAG, "User info error: ${connection.responseCode} - $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "User info exception", e)
            Result.failure(e)
        }
    }

    /**
     * Performs logout
     */
    suspend fun logout(idToken: String?) = withContext(Dispatchers.IO) {
        try {
            // Clear local tokens
            settingsRepository.clearAuth()

            // If we have an ID token, we can do RP-initiated logout
            if (idToken != null) {
                val logoutUrl = "${KeycloakConfig.END_SESSION_ENDPOINT}?" +
                    "id_token_hint=$idToken&" +
                    "post_logout_redirect_uri=${Uri.encode(KeycloakConfig.POST_LOGOUT_REDIRECT_URI)}"

                Log.d(TAG, "Logout URL: $logoutUrl")
                // The actual redirect to Keycloak logout would be done via browser
                // For mobile app, clearing local tokens is usually sufficient
            }

            Log.d(TAG, "Logout completed")
        } catch (e: Exception) {
            Log.e(TAG, "Logout exception", e)
        }
    }

    /**
     * Checks if the current token needs refresh
     */
    fun isTokenExpired(expiresAt: Long): Boolean {
        // Add 60 seconds buffer
        return System.currentTimeMillis() >= (expiresAt - 60000)
    }

    fun dispose() {
        authService.dispose()
    }
}

/**
 * User info from Keycloak
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
