package com.duq.android.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "duq_settings")

/**
 * Repository for app settings and authentication tokens.
 * Security: Tokens are stored in EncryptedSharedPreferences.
 * Non-sensitive settings use DataStore.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private const val TAG = "SettingsRepository"
        private const val ENCRYPTED_PREFS_NAME = "duq_secure_prefs"

        // Encrypted preferences keys (for tokens)
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val KEY_PORCUPINE_API_KEY = "porcupine_api_key"
        private const val KEY_DEVICE_ID = "device_id"
    }

    private object PreferencesKeys {
        // User info (DataStore - less sensitive)
        val USER_SUB = stringPreferencesKey("user_sub")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NAME = stringPreferencesKey("user_name")
        val USERNAME = stringPreferencesKey("username")
    }

    /**
     * Encrypted SharedPreferences for secure token storage.
     * Uses AES256 encryption with MasterKey from Android Keystore.
     */
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, falling back to regular prefs", e)
            // Fallback to regular SharedPreferences if encryption fails (shouldn't happen)
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // Access token flow (from encrypted storage)
    val accessToken: Flow<String> = flow {
        emit(encryptedPrefs.getString(KEY_ACCESS_TOKEN, "") ?: "")
    }

    // Refresh token flow (from encrypted storage)
    val refreshToken: Flow<String> = flow {
        emit(encryptedPrefs.getString(KEY_REFRESH_TOKEN, "") ?: "")
    }

    // ID token flow (from encrypted storage)
    val idToken: Flow<String> = flow {
        emit(encryptedPrefs.getString(KEY_ID_TOKEN, "") ?: "")
    }

    // Token expiration (from encrypted storage)
    val tokenExpiresAt: Flow<Long> = flow {
        emit(encryptedPrefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L))
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

    // Porcupine API key flow (from encrypted storage)
    val porcupineApiKey: Flow<String> = flow {
        emit(encryptedPrefs.getString(KEY_PORCUPINE_API_KEY, "") ?: "")
    }

    // Device ID for WebSocket connection (from encrypted storage)
    // Auto-generates UUID if not exists
    val deviceId: Flow<String> = flow {
        var id = encryptedPrefs.getString(KEY_DEVICE_ID, "") ?: ""
        if (id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString()
            encryptedPrefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        emit(id)
    }

    val isAuthenticated: Flow<Boolean> = flow {
        val token = encryptedPrefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        emit(token.isNotBlank())
    }

    val hasValidSettings: Flow<Boolean> = flow {
        val token = encryptedPrefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        // Only require authentication, Porcupine key is optional
        emit(token.isNotBlank())
    }

    /**
     * Get current access token synchronously (from encrypted storage)
     */
    suspend fun getAccessToken(): String {
        return encryptedPrefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
    }

    /**
     * Get current refresh token synchronously (from encrypted storage)
     */
    suspend fun getRefreshToken(): String {
        return encryptedPrefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
    }

    /**
     * Get current ID token synchronously (from encrypted storage)
     */
    suspend fun getIdToken(): String {
        return encryptedPrefs.getString(KEY_ID_TOKEN, "") ?: ""
    }

    /**
     * Get token expiration time (from encrypted storage)
     */
    suspend fun getTokenExpiresAt(): Long {
        return encryptedPrefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
    }

    /**
     * Check if token is expired (with 60s buffer)
     */
    suspend fun isTokenExpired(): Boolean {
        val expiresAt = getTokenExpiresAt()
        return System.currentTimeMillis() >= (expiresAt - 60000)
    }

    /**
     * Save Keycloak auth tokens (to encrypted storage)
     * Security: Uses EncryptedSharedPreferences with AES256 encryption
     */
    suspend fun saveAuthTokens(
        accessToken: String,
        refreshToken: String?,
        idToken: String?,
        expiresAt: Long
    ) {
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .apply {
                refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
                idToken?.let { putString(KEY_ID_TOKEN, it) }
            }
            .putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
            .apply()
    }

    /**
     * Update access token after refresh (to encrypted storage)
     */
    suspend fun updateAccessToken(
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long
    ) {
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .apply {
                refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
            }
            .putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
            .apply()
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
     * Save Porcupine API key (to encrypted storage)
     * Security: Uses EncryptedSharedPreferences with AES256 encryption
     */
    suspend fun savePorcupineApiKey(apiKey: String) {
        encryptedPrefs.edit()
            .putString(KEY_PORCUPINE_API_KEY, apiKey)
            .apply()
    }

    /**
     * Get Porcupine API key synchronously (from encrypted storage)
     */
    suspend fun getPorcupineApiKey(): String {
        return encryptedPrefs.getString(KEY_PORCUPINE_API_KEY, "") ?: ""
    }

    /**
     * Clear all auth data (logout)
     * Clears both encrypted token storage and user info from DataStore
     */
    suspend fun clearAuth() {
        // Clear encrypted tokens
        encryptedPrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ID_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .apply()

        // Clear user info from DataStore
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.USER_SUB)
            preferences.remove(PreferencesKeys.USER_EMAIL)
            preferences.remove(PreferencesKeys.USER_NAME)
            preferences.remove(PreferencesKeys.USERNAME)
        }
    }
}
