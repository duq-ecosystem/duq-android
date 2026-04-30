package com.duq.android.service

import android.app.AppOpsManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.util.Log
import com.duq.android.BuildConfig
import com.duq.android.DuqState
import com.duq.android.config.AppConfig
import com.duq.android.data.SettingsRepository
import com.duq.android.error.DuqError
import com.duq.android.network.DuqWebSocketClient
import com.duq.android.wakeword.WakeWordManager
import com.duq.android.wakeword.WakeWordManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DuqListenerService : Service(), VoiceServiceController {

    companion object {
        private const val TAG = "DuqListenerService"
        const val ACTION_START = "com.duq.android.START"
        const val ACTION_STOP = "com.duq.android.STOP"
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notificationManager: DuqNotificationManager
    @Inject lateinit var voiceCommandProcessor: VoiceCommandProcessor
    @Inject lateinit var webSocketClient: DuqWebSocketClient
    @Inject lateinit var wakeWordManagerFactory: WakeWordManagerFactory

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var wakeWordManager: WakeWordManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var isWakeWordInitialized = false

    private val _state = MutableStateFlow(DuqState.IDLE)
    override val state: StateFlow<DuqState> = _state

    private val _error = MutableStateFlow<DuqError?>(null)
    override val error: StateFlow<DuqError?> = _error

    private val stateCallback = object : VoiceCommandProcessor.StateCallback {
        override fun onStateChanged(state: DuqState) {
            _state.value = state
            notificationManager.updateNotification(state)
        }

        override fun onError(error: DuqError) {
            _error.value = error
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): DuqListenerService = this@DuqListenerService
        fun getController(): VoiceServiceController = this@DuqListenerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "🚀 SERVICE CREATED")
        Log.d(TAG, "Initializing audio player...")
        voiceCommandProcessor.initializePlayer()
        Log.d(TAG, "Acquiring wake lock...")
        acquireWakeLock()
        Log.d(TAG, "Connecting WebSocket...")
        connectWebSocket()
        Log.d(TAG, "Initializing wake word detection...")
        initializeWakeWord()
        Log.d(TAG, "═══════════════════════════════════════")
    }

    private fun connectWebSocket() {
        serviceScope.launch {
            try {
                webSocketClient.connect()
                Log.d(TAG, "WebSocket connection initiated")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect WebSocket: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                // Start as foreground service for background operation
                startForegroundServiceWithNotification()
                Log.d(TAG, "Started as foreground service")
            }
        }
        // Keep service running in background
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notification = notificationManager.createNotification(_state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                DuqNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(DuqNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    private fun initializeWakeWord() {
        if (isWakeWordInitialized) {
            Log.d(TAG, "Wake word already initialized, skipping")
            return
        }
        isWakeWordInitialized = true
        serviceScope.launch {
            startWakeWordManager()
        }
    }

    private fun isBackgroundRecordingAllowed(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_RECORD_AUDIO,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_RECORD_AUDIO,
                Process.myUid(),
                packageName
            )
        }
        // MODE_ALLOWED = 0, MODE_IGNORED = 1, MODE_ERRORED = 2, MODE_DEFAULT = 3, MODE_FOREGROUND = 4
        Log.d(TAG, "RECORD_AUDIO appops mode: $mode")
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private suspend fun startWakeWordManager() {
        try {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🎧 WAKE WORD MANAGER INITIALIZATION")

            // Try stored key first, fallback to BuildConfig
            var apiKey = settingsRepository.porcupineApiKey.first()
            if (apiKey.isBlank() && BuildConfig.PORCUPINE_API_KEY.isNotBlank()) {
                apiKey = BuildConfig.PORCUPINE_API_KEY
                Log.d(TAG, "Using Porcupine API key from BuildConfig")
            }
            Log.d(TAG, "Porcupine API key: ${if (apiKey.isBlank()) "NOT SET" else "SET (${apiKey.take(10)}...)"}")

            if (apiKey.isBlank()) {
                Log.e(TAG, "❌ Porcupine API key not configured")
                _state.value = DuqState.ERROR
                _error.value = DuqError.ConfigurationError.missingApiKey("Porcupine API key")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                return
            }

            // Note: Background recording check removed for app-only mode
            // App only works when open, so foreground mic permission is sufficient
            Log.d(TAG, "App-only mode: skipping background recording check")
            Log.d(TAG, "Wake word will work when app is open")

            // Clean up existing manager to prevent memory leak
            wakeWordManager?.let { manager ->
                Log.d(TAG, "Stopping existing wake word manager...")
                manager.stop()
                wakeWordManager = null
            }

            Log.d(TAG, "Creating new WakeWordManager instance...")
            val sensitivity = settingsRepository.getWakeWordSensitivitySync()
            Log.d(TAG, "Wake word sensitivity: $sensitivity")
            wakeWordManager = wakeWordManagerFactory.create(
                context = this@DuqListenerService,
                accessKey = apiKey,
                sensitivity = sensitivity,
                onWakeWordDetected = { onWakeWordDetected() },
                onError = { errorMessage ->
                    Log.e(TAG, "❌ Wake word error: $errorMessage")
                    _state.value = DuqState.ERROR
                    _error.value = if (errorMessage.contains("API key", ignoreCase = true)) {
                        DuqError.WakeWordError.invalidApiKey()
                    } else {
                        DuqError.WakeWordError.initFailed(RuntimeException(errorMessage))
                    }
                }
            )

            Log.d(TAG, "Starting wake word detection...")
            wakeWordManager?.start()

            _state.value = DuqState.IDLE
            notificationManager.updateNotification(_state.value)

            Log.d(TAG, "✅ WAKE WORD MANAGER READY")
            Log.d(TAG, "Listening for 'Hey Duck'...")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        } catch (e: Exception) {
            Log.e(TAG, "❌ WAKE WORD INITIALIZATION FAILED")
            Log.e(TAG, "Error: ${e.javaClass.simpleName}: ${e.message}", e)
            _state.value = DuqState.ERROR
            _error.value = DuqError.WakeWordError.initFailed(e)
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    private fun onWakeWordDetected() {
        Log.d(TAG, "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓")
        Log.d(TAG, "┃  🦆 WAKE WORD DETECTED: HEY DUCK  ┃")
        Log.d(TAG, "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛")
        serviceScope.launch { processVoiceCommand() }
    }

    private suspend fun processVoiceCommand() {
        try {
            Log.d(TAG, "Stopping wake word manager...")
            wakeWordManager?.stop()
            wakeWordManager = null
            Log.d(TAG, "Wake word manager stopped")

            Log.d(TAG, "Processing voice command...")
            val result = voiceCommandProcessor.processVoiceCommand(stateCallback)

            when (result) {
                is VoiceCommandProcessor.ProcessingResult.Success -> {
                    Log.d(TAG, "✅ Voice command processed successfully")
                }
                is VoiceCommandProcessor.ProcessingResult.Error -> {
                    Log.e(TAG, "❌ Voice command failed: ${result.error.message}")
                }
                is VoiceCommandProcessor.ProcessingResult.RecordingFailed -> {
                    Log.e(TAG, "❌ Recording failed")
                }
            }
        } finally {
            Log.d(TAG, "Restarting wake word detection...")
            isWakeWordInitialized = false
            initializeWakeWord()
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "duq:wakeword_detection"
        )
        // Acquire with 10-minute timeout for safety (prevents battery drain if service crashes)
        // The wake lock is renewed automatically by the periodic wake word processing
        wakeLock?.acquire(AppConfig.WAKE_LOCK_TIMEOUT_MS)
    }

    /**
     * Renew wake lock during active operations.
     * Call this periodically to prevent the lock from expiring during long sessions.
     */
    private fun renewWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            it.acquire(AppConfig.WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // VoiceServiceController implementation
    override fun startListening() {
        if (!isWakeWordInitialized) {
            initializeWakeWord()
        }
    }

    override fun stopListening() {
        wakeWordManager?.stop()
        wakeWordManager = null
        isWakeWordInitialized = false
        _state.value = DuqState.IDLE
    }

    override fun clearError() {
        _error.value = null
        _state.value = DuqState.IDLE
    }

    override fun onDestroy() {
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "🛑 SERVICE DESTROYING")
        Log.d(TAG, "Stopping wake word manager...")
        wakeWordManager?.stop()
        wakeWordManager = null  // Prevent memory leak
        Log.d(TAG, "Stopping recording...")
        voiceCommandProcessor.stopRecording()
        Log.d(TAG, "Releasing audio player...")
        voiceCommandProcessor.releasePlayer()
        Log.d(TAG, "Disconnecting WebSocket...")
        webSocketClient.disconnect()
        Log.d(TAG, "Releasing wake lock...")
        releaseWakeLock()
        Log.d(TAG, "Cancelling service scope...")
        serviceScope.cancel()
        Log.d(TAG, "✅ SERVICE DESTROYED")
        Log.d(TAG, "═══════════════════════════════════════")
        super.onDestroy()
    }
}
