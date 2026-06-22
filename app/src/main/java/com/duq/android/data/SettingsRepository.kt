package com.duq.android.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * No @Singleton/@Inject here — provided via AppModule.provideSettingsRepository
 * which supplies the @ApplicationContext. Avoid duplicate binding.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private const val TAG = "SettingsRepository"
        private const val PREFS_NAME = "duq_secure_settings"
        private const val KEY_PORCUPINE_API_KEY = "porcupine_api_key"
        private const val KEY_WAKE_WORD_SENSITIVITY = "wake_word_sensitivity"
        private const val KEY_SILENCE_TIMEOUT_MS = "silence_timeout_ms"
        private const val KEY_LAST_REPORTED_LOCATION = "last_reported_location"

        const val DEFAULT_WAKE_WORD_SENSITIVITY = 0.9f
        const val DEFAULT_SILENCE_TIMEOUT_MS = 2000L
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(context, PREFS_NAME, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        } catch (e: Exception) {
            Log.e(TAG, "Encrypted prefs failed, using plain: ${e.message}")
            context.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
        }
    }

    // Авторизация устройства — build-time edge-token (BuildConfig.SERVER_TOKEN), не
    // per-device пейринг. Поля device/node/bootstrap/gateway (Ed25519/legacy) удалены.

    /** Last lat/lng reported to DUQ — used to suppress duplicate reports across restarts. */
    fun getLastReportedLocation(): Pair<Double, Double>? {
        val s = prefs.getString(KEY_LAST_REPORTED_LOCATION, "") ?: ""
        if (s.isBlank()) return null
        val parts = s.split(",")
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        return lat to lng
    }

    fun saveLastReportedLocation(lat: Double, lng: Double) {
        prefs.edit().putString(KEY_LAST_REPORTED_LOCATION, "$lat,$lng").apply()
    }

    // Porcupine — reactive flow backed by MutableStateFlow
    private val _porcupineApiKey = MutableStateFlow(prefs.getString(KEY_PORCUPINE_API_KEY, "") ?: "")
    val porcupineApiKey: Flow<String> = _porcupineApiKey
    fun getPorcupineApiKey(): String = _porcupineApiKey.value
    fun savePorcupineApiKey(key: String) {
        prefs.edit().putString(KEY_PORCUPINE_API_KEY, key).apply()
        _porcupineApiKey.value = key
    }

    // Wake word sensitivity — reactive
    private val _wakeWordSensitivity = MutableStateFlow(prefs.getFloat(KEY_WAKE_WORD_SENSITIVITY, DEFAULT_WAKE_WORD_SENSITIVITY))
    val wakeWordSensitivity: Flow<Float> = _wakeWordSensitivity
    fun getWakeWordSensitivitySync(): Float = _wakeWordSensitivity.value
    fun saveWakeWordSensitivity(v: Float) {
        val clamped = v.coerceIn(0.5f, 1.0f)
        prefs.edit().putFloat(KEY_WAKE_WORD_SENSITIVITY, clamped).apply()
        _wakeWordSensitivity.value = clamped
    }

    // Silence timeout — reactive
    private val _silenceTimeoutMs = MutableStateFlow(prefs.getLong(KEY_SILENCE_TIMEOUT_MS, DEFAULT_SILENCE_TIMEOUT_MS))
    val silenceTimeoutMs: Flow<Long> = _silenceTimeoutMs
    fun getSilenceTimeoutMsSync(): Long = _silenceTimeoutMs.value
    fun saveSilenceTimeoutMs(v: Long) {
        val clamped = v.coerceIn(1000L, 4000L)
        prefs.edit().putLong(KEY_SILENCE_TIMEOUT_MS, clamped).apply()
        _silenceTimeoutMs.value = clamped
    }
}
