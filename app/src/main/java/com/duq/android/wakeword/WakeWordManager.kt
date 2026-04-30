package com.duq.android.wakeword

import ai.picovoice.porcupine.PorcupineActivationException
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.duq.android.config.AppConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordManager(
    private val context: Context,
    private val accessKey: String,
    private val sensitivity: Float = AppConfig.WAKE_WORD_SENSITIVITY,
    private val onWakeWordDetected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var porcupineManager: PorcupineManager? = null
    private val isListening = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)
    private val wakeWordTriggered = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var detectionStartTime = 0L

    companion object {
        private const val TAG = "WakeWordManager"
    }

    fun start() {
        if (isListening.get() || isStopping.get()) {
            Log.d(TAG, "Already listening or stopping")
            return
        }

        wakeWordTriggered.set(false)

        try {
            val builder = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setSensitivity(sensitivity)

            // Try to load custom "hey duq" wake word file
            val customKeywordPath = getCustomKeywordPath()
            if (customKeywordPath != null) {
                Log.d(TAG, "Using custom wake word: $customKeywordPath")
                builder.setKeywordPath(customKeywordPath)
            } else {
                // Fallback: use built-in keyword for development
                // In production, the custom .ppn file should be present
                Log.w(TAG, "Custom wake word not found, using fallback")
                onError("Custom wake word file not found. Please place $AppConfig.WAKE_WORD_FILENAME in app's external files directory.")
                return
            }

            porcupineManager = builder.build(context, object : PorcupineManagerCallback {
                override fun invoke(keywordIndex: Int) {
                    // Check all flags before processing
                    if (!isListening.get() || isStopping.get()) {
                        return
                    }
                    if (wakeWordTriggered.compareAndSet(false, true)) {
                        Log.d(TAG, "Wake word detected!")
                        // Stop listening immediately before posting callback
                        isListening.set(false)
                        mainHandler.post {
                            onWakeWordDetected()
                        }
                    }
                }
            })

            porcupineManager?.start()
            isListening.set(true)
            detectionStartTime = System.currentTimeMillis()
            Log.d(TAG, "Started listening for wake word")
        } catch (e: PorcupineActivationException) {
            Log.e(TAG, "Porcupine activation error", e)
            onError("Invalid Porcupine API key: ${e.message}")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine error", e)
            onError("Porcupine error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake word detection", e)
            onError("Error: ${e.message}")
        }
    }

    /**
     * Get path to custom wake word file.
     * The file should be placed at: /storage/emulated/0/Android/data/com.duq.android/files/hey_duq.ppn
     */
    private fun getCustomKeywordPath(): String? {
        // Try external files directory first (user can place file here)
        val externalFile = context.getExternalFilesDir(null)?.resolve(AppConfig.WAKE_WORD_FILENAME)
        if (externalFile?.exists() == true) {
            return externalFile.absolutePath
        }

        // Try internal files directory
        val internalFile = File(context.filesDir, AppConfig.WAKE_WORD_FILENAME)
        if (internalFile.exists()) {
            return internalFile.absolutePath
        }

        // Try assets (copy to internal storage on first run)
        try {
            context.assets.open(AppConfig.WAKE_WORD_FILENAME).use { input ->
                internalFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (internalFile.exists()) {
                return internalFile.absolutePath
            }
        } catch (e: Exception) {
            Log.d(TAG, "Wake word file not in assets: ${e.message}")
        }

        return null
    }

    fun stop() {
        if (!isListening.getAndSet(false) && !wakeWordTriggered.get()) {
            return
        }

        if (!isStopping.compareAndSet(false, true)) {
            Log.d(TAG, "Already stopping")
            return
        }

        val manager = porcupineManager
        porcupineManager = null

        if (manager != null) {
            try {
                manager.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping Porcupine", e)
            }

            // Post delete with delay to allow audio thread to fully stop
            // Uses Handler instead of Thread.sleep to avoid blocking
            mainHandler.postDelayed({
                try {
                    manager.delete()
                    Log.d(TAG, "Porcupine resources released")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting Porcupine", e)
                }
                isStopping.set(false)
            }, 500)
        } else {
            isStopping.set(false)
        }
        Log.d(TAG, "Stopped listening")
    }

    fun isActive(): Boolean = isListening.get() && !isStopping.get()
}
