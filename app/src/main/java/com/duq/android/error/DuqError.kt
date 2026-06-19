package com.duq.android.error

/**
 * Sealed class hierarchy for all Duq errors.
 * Use this throughout the app for consistent error handling.
 */
sealed class DuqError(val message: String) {

    /** Network-related errors (timeout, connection, server errors) */
    class NetworkError(
        message: String,
        val code: Int? = null,
        val isRetryable: Boolean = true
    ) : DuqError(message) {
        companion object {
            fun timeout(message: String = "Request timed out") = NetworkError(message, isRetryable = true)
            fun noConnection(message: String = "No internet connection") = NetworkError(message, isRetryable = true)
            fun serverError(code: Int, message: String) = NetworkError(message, code, isRetryable = code >= 500)
            fun clientError(code: Int, message: String) = NetworkError(message, code, isRetryable = false)
        }
    }

    /** Audio recording/playback errors */
    class AudioError(message: String, val cause: Throwable? = null) : DuqError(message) {
        companion object {
            fun recordingFailed(cause: Throwable? = null) = AudioError("Recording failed", cause)
            fun playbackFailed(cause: Throwable? = null) = AudioError("Playback failed", cause)
            fun noAudioData() = AudioError("No audio data received")
        }
    }

    /** Wake word detection errors */
    class WakeWordError(message: String, val cause: Throwable? = null) : DuqError(message) {
        companion object {
            fun initFailed(cause: Throwable? = null) = WakeWordError("Wake word initialization failed", cause)
            fun invalidApiKey() = WakeWordError("Invalid Porcupine API key")
        }
    }

    /** Permission-related errors */
    class PermissionError(val permission: String) : DuqError("Missing permission: $permission") {
        companion object {
            fun microphone() = PermissionError("android.permission.RECORD_AUDIO")
        }
    }

    /** Configuration/setup errors */
    class ConfigurationError(message: String) : DuqError(message) {
        companion object {
            fun missingApiKey(keyName: String) = ConfigurationError("$keyName not configured")
            fun invalidConfig(reason: String) = ConfigurationError("Invalid configuration: $reason")
        }
    }

    /** Authentication errors */
    class AuthError(message: String, val requiresReauth: Boolean = false) : DuqError(message) {
        companion object {
            fun tokenExpired() = AuthError("Session expired", requiresReauth = true)
            fun invalidToken() = AuthError("Invalid authentication", requiresReauth = true)
            fun refreshFailed(reason: String) = AuthError("Token refresh failed: $reason", requiresReauth = true)
        }
    }

    /** Convert to user-friendly display message */
    fun toDisplayMessage(): String = when (this) {
        is NetworkError -> when {
            code != null && code >= 500 -> "Server error. Please try again."
            code == 401 || code == 403 -> "Authentication error. Please re-login."
            code == 404 -> "Not found."
            message.contains("timeout", ignoreCase = true) || message.contains("timed out", ignoreCase = true) -> "Connection timed out. Please try again."
            else -> "Network error. Check your connection."
        }
        is AudioError -> "Audio error: $message"
        is WakeWordError -> "Voice activation error: $message"
        is PermissionError -> "Permission required: $permission"
        is ConfigurationError -> "Setup error: $message"
        is AuthError -> if (requiresReauth) "Please log in again" else message
    }
}
