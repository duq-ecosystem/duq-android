package com.jarvis.android.wakeword

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineActivationException
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jarvis.android.config.AppConfig
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordManager(
    private val context: Context,
    private val accessKey: String,
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
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setSensitivity(AppConfig.WAKE_WORD_SENSITIVITY)
                .build(context, object : PorcupineManagerCallback {
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

    fun stop() {
        if (!isListening.getAndSet(false) && !wakeWordTriggered.get()) {
            return
        }

        if (!isStopping.compareAndSet(false, true)) {
            Log.d(TAG, "Already stopping")
            return
        }

        try {
            val manager = porcupineManager
            porcupineManager = null

            if (manager != null) {
                try {
                    manager.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping Porcupine", e)
                }

                // Wait for audio thread to fully stop before deleting
                Thread.sleep(500)

                try {
                    manager.delete()
                    Log.d(TAG, "Porcupine resources released")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting Porcupine", e)
                }
            }
            Log.d(TAG, "Stopped listening")
        } finally {
            isStopping.set(false)
        }
    }

    fun isActive(): Boolean = isListening.get() && !isStopping.get()
}
