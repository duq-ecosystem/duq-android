package com.duq.android.config

/**
 * Centralized application configuration.
 * All timeouts and limits in one place for easy tuning.
 */
object AppConfig {
    // Network timeouts (seconds)
    const val CONNECT_TIMEOUT_S = 30L
    const val READ_TIMEOUT_S = 60L
    const val WRITE_TIMEOUT_S = 120L

    // Retry configuration
    const val MAX_RETRIES = 3
    const val INITIAL_RETRY_DELAY_MS = 1000L
    const val MAX_RETRY_DELAY_MS = 30000L
    const val RETRY_MULTIPLIER = 2.0

    // Audio recording limits (milliseconds)
    const val MIN_RECORDING_MS = 1000L
    const val MAX_RECORDING_MS = 10000L

    // Voice Activity Detection
    const val VAD_SILENCE_TIMEOUT_MS = 2000L
    const val VAD_MIN_RECORDING_MS = 500L
    const val SILERO_SILENCE_DURATION_MS = 800
    const val SILERO_SPEECH_DURATION_MS = 100
    const val SILERO_FRAME_SIZE = 512

    // Wake word
    const val WAKE_WORD_SENSITIVITY = 0.5f

    // Service
    const val WAKE_LOCK_TIMEOUT_MS = 600000L  // 10 minutes

    // Auth
    const val AUTH_TIMEOUT_S = 10L
    const val AUTH_TIMEOUT_MS = AUTH_TIMEOUT_S * 1000  // For HttpURLConnection (uses Int ms)
    const val DEFAULT_TOKEN_EXPIRES_S = 300  // 5 minutes

    // Pagination
    const val DEFAULT_MESSAGES_LIMIT = 50
}
