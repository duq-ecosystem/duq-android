package com.duq.android.wakeword

import android.content.Context
import com.duq.android.config.AppConfig

/**
 * Factory interface for creating WakeWordManager instances.
 * Allows Hilt injection and testing without Porcupine dependency.
 */
interface WakeWordManagerFactory {
    /**
     * Create a new WakeWordManager instance.
     *
     * @param context Application context
     * @param accessKey Porcupine API key
     * @param sensitivity Wake word detection sensitivity (0.0-1.0)
     * @param onWakeWordDetected Callback when wake word is detected
     * @param onError Callback for errors
     * @return WakeWordManager instance
     */
    fun create(
        context: Context,
        accessKey: String,
        sensitivity: Float = AppConfig.WAKE_WORD_SENSITIVITY,
        onWakeWordDetected: () -> Unit,
        onError: (String) -> Unit
    ): WakeWordManager
}

/**
 * Default implementation that creates real WakeWordManager instances.
 */
class DefaultWakeWordManagerFactory : WakeWordManagerFactory {
    override fun create(
        context: Context,
        accessKey: String,
        sensitivity: Float,
        onWakeWordDetected: () -> Unit,
        onError: (String) -> Unit
    ): WakeWordManager {
        return WakeWordManager(
            context = context,
            accessKey = accessKey,
            sensitivity = sensitivity,
            onWakeWordDetected = onWakeWordDetected,
            onError = onError
        )
    }
}
