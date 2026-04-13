package com.duq.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "duq_settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        // Keycloak tokens
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val ID_TOKEN = stringPreferencesKey("id_token")
        val TOKEN_EXPIRES_AT = longPreferencesKey("token_expires_at")

        // User info
        val USER_SUB = stringPreferencesKey("user_sub")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NAME = stringPreferencesKey("user_name")
        val USERNAME = stringPreferencesKey("username")

        // App settings
        val PORCUPINE_API_KEY = stringPreferencesKey("porcupine_api_key")
    }

    // Access token flow
    val accessToken: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] ?: ""
        }

    // Refresh token flow
    val refreshToken: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.REFRESH_TOKEN] ?: ""
        }

    // ID token flow
    val idToken: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ID_TOKEN] ?: ""
        }

    // Token expiration
    val tokenExpiresAt: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.TOKEN_EXPIRES_AT] ?: 0L
        }

    // User info
    val userSub: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USER_SUB] ?: ""
        }

    val userEmail: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USER_EMAIL] ?: ""
        }

    val userName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USER_NAME] ?: ""
        }

    val username: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USERNAME] ?: ""
        }

    val porcupineApiKey: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.PORCUPINE_API_KEY] ?: ""
        }

    val isAuthenticated: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            val token = preferences[PreferencesKeys.ACCESS_TOKEN] ?: ""
            token.isNotBlank()
        }

    val hasValidSettings: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            val token = preferences[PreferencesKeys.ACCESS_TOKEN] ?: ""
            val apiKey = preferences[PreferencesKeys.PORCUPINE_API_KEY] ?: ""
            token.isNotBlank() && apiKey.isNotBlank()
        }

    /**
     * Get current access token synchronously
     */
    suspend fun getAccessToken(): String {
        return accessToken.first()
    }

    /**
     * Get current refresh token synchronously
     */
    suspend fun getRefreshToken(): String {
        return refreshToken.first()
    }

    /**
     * Get current ID token synchronously
     */
    suspend fun getIdToken(): String {
        return idToken.first()
    }

    /**
     * Get token expiration time
     */
    suspend fun getTokenExpiresAt(): Long {
        return tokenExpiresAt.first()
    }

    /**
     * Check if token is expired (with 60s buffer)
     */
    suspend fun isTokenExpired(): Boolean {
        val expiresAt = getTokenExpiresAt()
        return System.currentTimeMillis() >= (expiresAt - 60000)
    }

    /**
     * Save Keycloak auth tokens
     */
    suspend fun saveAuthTokens(
        accessToken: String,
        refreshToken: String?,
        idToken: String?,
        expiresAt: Long
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] = accessToken
            refreshToken?.let { preferences[PreferencesKeys.REFRESH_TOKEN] = it }
            idToken?.let { preferences[PreferencesKeys.ID_TOKEN] = it }
            preferences[PreferencesKeys.TOKEN_EXPIRES_AT] = expiresAt
        }
    }

    /**
     * Update access token (after refresh)
     */
    suspend fun updateAccessToken(
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] = accessToken
            refreshToken?.let { preferences[PreferencesKeys.REFRESH_TOKEN] = it }
            preferences[PreferencesKeys.TOKEN_EXPIRES_AT] = expiresAt
        }
    }

    /**
     * Save user info from Keycloak
     */
    suspend fun saveUserInfo(
        sub: String,
        email: String,
        name: String,
        username: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_SUB] = sub
            preferences[PreferencesKeys.USER_EMAIL] = email
            preferences[PreferencesKeys.USER_NAME] = name
            preferences[PreferencesKeys.USERNAME] = username
        }
    }

    /**
     * Save Porcupine API key
     */
    suspend fun savePorcupineApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PORCUPINE_API_KEY] = apiKey
        }
    }

    /**
     * Clear all auth data (logout)
     */
    suspend fun clearAuth() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ACCESS_TOKEN)
            preferences.remove(PreferencesKeys.REFRESH_TOKEN)
            preferences.remove(PreferencesKeys.ID_TOKEN)
            preferences.remove(PreferencesKeys.TOKEN_EXPIRES_AT)
            preferences.remove(PreferencesKeys.USER_SUB)
            preferences.remove(PreferencesKeys.USER_EMAIL)
            preferences.remove(PreferencesKeys.USER_NAME)
            preferences.remove(PreferencesKeys.USERNAME)
        }
    }
}
