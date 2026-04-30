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
import com.duq.android.auth.AccountTokenStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "duq_settings")

/**
 * Repository for app settings and authentication tokens.
 *
 * Token storage strategy:
 * - PRIMARY: AccountManager (survives app data clear / pm clear)
 * - FALLBACK: EncryptedSharedPreferences (for non-critical data like device ID, Porcupine key)
 *
 * This ensures biometric login works even after app data is cleared.
 */
class SettingsRepository(
    private val context: Context,
    private val accountTokenStorage: AccountTokenStorage? = null
) {

    companion object {
        private const val TAG = "SettingsRepository"
        private const val ENCRYPTED_PREFS_NAME = "duq_secure_prefs"

        // EncryptedSharedPreferences keys (for non-token data only)
        private const val KEY_PORCUPINE_API_KEY = "porcupine_api_key"
        private const val KEY_DEVICE_ID = "device_id"

        // User-configurable settings keys
        private const val KEY_WAKE_WORD_SENSITIVITY = "wake_word_sensitivity"
        private const val KEY_SILENCE_TIMEOUT_MS = "silence_timeout_ms"
        private const val KEY_MAX_RECORDING_MS = "max_recording_ms"

        // Defaults
        const val DEFAULT_WAKE_WORD_SENSITIVITY = 0.9f
        const val DEFAULT_SILENCE_TIMEOUT_MS = 2000L
        const val DEFAULT_MAX_RECORDING_MS = 10000L
    }

    // Lazy initialization of AccountTokenStorage if not injected
    private val tokenStorage: AccountTokenStorage by lazy {
        accountTokenStorage ?: AccountTokenStorage(context)
    }

    private object PreferencesKeys {
        // User info (DataStore - less sensitive, also backed by AccountManager)
        val USER_SUB = stringPreferencesKey("user_sub")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NAME = stringPreferencesKey("user_name")
        val USERNAME = stringPreferencesKey("username")
    }

    /**
     * Encrypted SharedPreferences for secure storage of non-token data.
     * Used for: Porcupine API key, Device ID
     * NOTE: Tokens are stored in AccountManager, not here.
     */
    private val encryptedPrefs: SharedPreferences by lazy {
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
    }

    /**
     * Flag indicating if encrypted storage is available.
     */
    val isEncryptionAvailable: Boolean by lazy {
        try {
            encryptedPrefs
            true
        } catch (e: Exception) {
            Log.e(TAG, "Encrypted storage unavailable: ${e.message}")
            false
        }
    }

    // ========== TOKEN FLOWS (from AccountManager) ==========

    val accessToken: Flow<String> = flow {
        emit(tokenStorage.getAccessToken())
    }

    val refreshToken: Flow<String> = flow {
        emit(tokenStorage.getRefreshToken())
    }

    val idToken: Flow<String> = flow {
        emit(tokenStorage.getIdToken())
    }

    val tokenExpiresAt: Flow<Long> = flow {
        emit(tokenStorage.getExpiresAt())
    }

    // ========== USER INFO FLOWS ==========

    // Primary source: AccountManager (survives pm clear)
    // Fallback: DataStore (for backward compatibility)
    val userSub: Flow<String> = flow {
        val fromAccount = tokenStorage.getUserSub()
        if (fromAccount.isNotBlank()) {
            emit(fromAccount)
        } else {
            emit(context.dataStore.data.first()[PreferencesKeys.USER_SUB] ?: "")
        }
    }

    val userEmail: Flow<String> = flow {
        val fromAccount = tokenStorage.getUserEmail()
        if (fromAccount.isNotBlank()) {
            emit(fromAccount)
        } else {
            emit(context.dataStore.data.first()[PreferencesKeys.USER_EMAIL] ?: "")
        }
    }

    val userName: Flow<String> = flow {
        val fromAccount = tokenStorage.getUserName()
        if (fromAccount.isNotBlank()) {
            emit(fromAccount)
        } else {
            emit(context.dataStore.data.first()[PreferencesKeys.USER_NAME] ?: "")
        }
    }

    val username: Flow<String> = flow {
        val fromAccount = tokenStorage.getUsername()
        if (fromAccount.isNotBlank()) {
            emit(fromAccount)
        } else {
            emit(context.dataStore.data.first()[PreferencesKeys.USERNAME] ?: "")
        }
    }

    // ========== OTHER SETTINGS ==========

    val porcupineApiKey: Flow<String> = flow {
        emit(encryptedPrefs.getString(KEY_PORCUPINE_API_KEY, "") ?: "")
    }

    val deviceId: Flow<String> = flow {
        var id = encryptedPrefs.getString(KEY_DEVICE_ID, "") ?: ""
        if (id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString()
            encryptedPrefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        emit(id)
    }

    val isAuthenticated: Flow<Boolean> = flow {
        emit(tokenStorage.isAuthenticated())
    }

    val hasValidSettings: Flow<Boolean> = flow {
        emit(tokenStorage.isAuthenticated())
    }

    // ========== USER-CONFIGURABLE SETTINGS ==========

    val wakeWordSensitivity: Flow<Float> = flow {
        emit(encryptedPrefs.getFloat(KEY_WAKE_WORD_SENSITIVITY, DEFAULT_WAKE_WORD_SENSITIVITY))
    }

    val silenceTimeoutMs: Flow<Long> = flow {
        emit(encryptedPrefs.getLong(KEY_SILENCE_TIMEOUT_MS, DEFAULT_SILENCE_TIMEOUT_MS))
    }

    val maxRecordingMs: Flow<Long> = flow {
        emit(encryptedPrefs.getLong(KEY_MAX_RECORDING_MS, DEFAULT_MAX_RECORDING_MS))
    }

    fun getWakeWordSensitivitySync(): Float =
        encryptedPrefs.getFloat(KEY_WAKE_WORD_SENSITIVITY, DEFAULT_WAKE_WORD_SENSITIVITY)

    fun getSilenceTimeoutMsSync(): Long =
        encryptedPrefs.getLong(KEY_SILENCE_TIMEOUT_MS, DEFAULT_SILENCE_TIMEOUT_MS)

    fun getMaxRecordingMsSync(): Long =
        encryptedPrefs.getLong(KEY_MAX_RECORDING_MS, DEFAULT_MAX_RECORDING_MS)

    fun saveWakeWordSensitivity(value: Float) {
        encryptedPrefs.edit().putFloat(KEY_WAKE_WORD_SENSITIVITY, value.coerceIn(0.5f, 1.0f)).apply()
    }

    fun saveSilenceTimeoutMs(value: Long) {
        encryptedPrefs.edit().putLong(KEY_SILENCE_TIMEOUT_MS, value.coerceIn(1000L, 4000L)).apply()
    }

    fun saveMaxRecordingMs(value: Long) {
        encryptedPrefs.edit().putLong(KEY_MAX_RECORDING_MS, value.coerceIn(5000L, 30000L)).apply()
    }

    // ========== SYNCHRONOUS METHODS (for OkHttp Interceptor) ==========

    fun getAccessTokenSync(): String = tokenStorage.getAccessToken()

    fun getRefreshTokenSync(): String = tokenStorage.getRefreshToken()

    fun getTokenExpiresAtSync(): Long = tokenStorage.getExpiresAt()

    fun updateAccessTokenSync(accessToken: String, refreshToken: String?, expiresAt: Long) {
        tokenStorage.updateAccessToken(accessToken, refreshToken, expiresAt)
    }

    // ========== SUSPEND METHODS ==========

    suspend fun getAccessToken(): String = tokenStorage.getAccessToken()

    suspend fun getRefreshToken(): String = tokenStorage.getRefreshToken()

    suspend fun getIdToken(): String = tokenStorage.getIdToken()

    suspend fun getTokenExpiresAt(): Long = tokenStorage.getExpiresAt()

    suspend fun isTokenExpired(): Boolean = tokenStorage.isTokenExpired()

    /**
     * Save Keycloak auth tokens to AccountManager.
     * These survive app data clear (pm clear).
     */
    suspend fun saveAuthTokens(
        accessToken: String,
        refreshToken: String?,
        idToken: String?,
        expiresAt: Long
    ) {
        tokenStorage.saveAuthTokens(accessToken, refreshToken, idToken, expiresAt)
        Log.d(TAG, "Auth tokens saved to AccountManager")
    }

    suspend fun updateAccessToken(accessToken: String, refreshToken: String?, expiresAt: Long) {
        tokenStorage.updateAccessToken(accessToken, refreshToken, expiresAt)
    }

    /**
     * Save user info to both AccountManager and DataStore.
     * AccountManager is primary (survives pm clear), DataStore is backup.
     */
    suspend fun saveUserInfo(sub: String, email: String, name: String, username: String) {
        // Save to AccountManager (primary, survives pm clear)
        tokenStorage.saveUserInfo(sub, email, name, username)

        // Also save to DataStore (backward compatibility)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_SUB] = sub
            preferences[PreferencesKeys.USER_EMAIL] = email
            preferences[PreferencesKeys.USER_NAME] = name
            preferences[PreferencesKeys.USERNAME] = username
        }
    }

    suspend fun savePorcupineApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(KEY_PORCUPINE_API_KEY, apiKey).apply()
    }

    suspend fun getPorcupineApiKey(): String {
        return encryptedPrefs.getString(KEY_PORCUPINE_API_KEY, "") ?: ""
    }

    fun clearTokensSync() {
        tokenStorage.clearTokens()
        Log.d(TAG, "Tokens cleared from AccountManager")
    }

    /**
     * Clear all auth data (logout).
     * Clears AccountManager tokens and DataStore user info.
     */
    suspend fun clearAuth() {
        // Clear from AccountManager
        tokenStorage.clearTokens()

        // Clear from DataStore
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.USER_SUB)
            preferences.remove(PreferencesKeys.USER_EMAIL)
            preferences.remove(PreferencesKeys.USER_NAME)
            preferences.remove(PreferencesKeys.USERNAME)
        }

        Log.d(TAG, "Auth cleared from AccountManager and DataStore")
    }
}
