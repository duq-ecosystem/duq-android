package com.duq.android.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.duq.android.BuildConfig
import com.duq.android.DuqState
import com.duq.android.data.SettingsRepository
import com.duq.android.error.DuqError
import com.duq.android.location.LocationReporter
import com.duq.android.network.openclaw.OpenClawGatewayClient
import com.duq.android.wakeword.WakeWordManager
import com.duq.android.wakeword.WakeWordManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Lean background service — maintains WebSocket connection only.
 * No microphone, no wake word in background.
 *
 * Wake word is managed by MainScreen (foreground only, on resume/pause).
 * Incoming DUQ messages → system notification when app is in background.
 */
@AndroidEntryPoint
class DuqListenerService : Service(), VoiceServiceController {

    companion object {
        private const val TAG = "DuqListenerService"
        const val ACTION_START = "com.duq.android.START"
        const val ACTION_STOP = "com.duq.android.STOP"
        // Live instance — node screen.record needs to raise the mediaProjection
        // FGS type on this exact running service before getMediaProjection().
        @Volatile var instance: DuqListenerService? = null
            private set
    }

    /** Add the mediaProjection FGS type (Android 14+ requires it before capture). */
    fun raiseMediaProjectionForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val n = notificationManager.createServiceNotification()
        // location/camera типы — только при наличии runtime-разрешения (иначе
        // SecurityException роняет сервис, см. startForegroundServiceWithNotification).
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        if (hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (hasPermission(android.Manifest.permission.CAMERA)) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        runCatching { startForeground(DuqNotificationManager.SERVICE_NOTIFICATION_ID, n, type) }
            .onFailure { Log.e(TAG, "raiseMediaProjectionForeground: ${it.message}") }
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notificationManager: DuqNotificationManager
    @Inject lateinit var voiceCommandProcessor: VoiceCommandProcessor
    @Inject lateinit var gatewayClient: OpenClawGatewayClient
    @Inject lateinit var nodeClient: com.duq.android.network.openclaw.OpenClawNodeClient
    @Inject lateinit var wakeWordManagerFactory: WakeWordManagerFactory
    @Inject lateinit var locationReporter: LocationReporter

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var wakeWordManager: WakeWordManager? = null
    private val isWakeWordInitialized = AtomicBoolean(false)

    // Wake-word events go through Log only, which HyperOS/MIUI throttles — mirror
    // the key transitions to the FileLogger so listening/detection is observable.
    private val flog by lazy { com.duq.android.logging.FileLogger(applicationContext) }

    // Accumulate streaming text per runId for background notifications
    private val messageBuffers = ConcurrentHashMap<String, StringBuilder>()

    private val _state = MutableStateFlow(DuqState.IDLE)
    override val state: StateFlow<DuqState> = _state

    private val _error = MutableStateFlow<DuqError?>(null)
    override val error: StateFlow<DuqError?> = _error

    private val stateCallback = object : VoiceCommandProcessor.StateCallback {
        override fun onStateChanged(state: DuqState) { _state.value = state }
        override fun onError(error: DuqError) { _error.value = error }
    }

    inner class LocalBinder : Binder() {
        fun getController(): VoiceServiceController = this@DuqListenerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "SERVICE CREATED — lean WS mode")
        voiceCommandProcessor.initializePlayer()
        gatewayClient.start()      // operator session: phone → bot (chat)
        nodeClient.start()         // node session: bot → phone (node.invoke commands)
        collectIncomingMessages()
        locationReporter.start(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            ACTION_START -> startForegroundServiceWithNotification()
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notification = notificationManager.createServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // dataSync (WS) + location (reports) + camera (node camera.snap). Camera type
            // is required for background camera access on Android 11+. mediaProjection is
            // added dynamically at screen.record time (it can't be declared without an
            // active projection token).
            // ⚠️ targetSDK34: startForeground с type=location/camera БЕЗ соответствующего
            // runtime-разрешения кидает SecurityException и роняет ВЕСЬ сервис (так app
            // крашился после `pm clear` — разрешения сброшены). Поэтому тип добавляем только
            // когда право реально выдано; иначе стартуем как dataSync и не падаем (location/
            // camera просто не работают, пока юзер не выдаст разрешение в UI).
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                    hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
                if (hasPermission(android.Manifest.permission.CAMERA)) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
            }
            startForeground(DuqNotificationManager.SERVICE_NOTIFICATION_ID, notification, type)
        } else {
            startForeground(DuqNotificationManager.SERVICE_NOTIFICATION_ID, notification)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Collect chat events — buffer text, show notification when app is in background */
    private fun collectIncomingMessages() {
        serviceScope.launch {
            gatewayClient.chatEvents.collect { event ->
                when (event.state) {
                    "delta" -> {
                        val text = event.deltaText ?: return@collect
                        messageBuffers.getOrPut(event.runId) { StringBuilder() }.append(text)
                    }
                    "final" -> {
                        // Prefer the server's authoritative cumulative text (same as the UI);
                        // fall back to the locally-accumulated delta buffer.
                        val raw = event.fullText ?: messageBuffers[event.runId]?.toString()
                        messageBuffers.remove(event.runId)
                        val text = raw?.let { com.duq.android.util.ReplyText.clean(it) } ?: return@collect
                        if (text.isNotBlank() && !com.duq.android.MainActivity.isInForeground) {
                            notificationManager.showMessageNotification(text)
                        }
                    }
                    "error", "aborted" -> messageBuffers.remove(event.runId)
                }
            }
        }
    }

    // VoiceServiceController — wake word managed here, triggered by MainScreen on resume/pause

    override fun startListening() {
        // Atomic check-and-set: two rapid lifecycle events can't both start the manager.
        if (isWakeWordInitialized.compareAndSet(false, true)) {
            serviceScope.launch { startWakeWordManager() }
        }
    }

    override fun stopListening() {
        wakeWordManager?.stop()
        wakeWordManager = null
        isWakeWordInitialized.set(false)
        _state.value = DuqState.IDLE
    }

    override fun clearError() {
        _error.value = null
        _state.value = DuqState.IDLE
    }

    private suspend fun startWakeWordManager() {
        // Wake word is permanently OFF (Porcupine hit its free-tier activation limit;
        // openWakeWord planned). Hard-stop here so NO code path can open the Porcupine
        // mic in the background — guarantees the mic privacy indicator never lights from
        // wake word. Re-enable by removing this guard once openWakeWord lands.
        flog.i(TAG, "Wake word disabled — not opening mic")
        isWakeWordInitialized.set(false)
        return
        @Suppress("UNREACHABLE_CODE")
        try {
            var apiKey = settingsRepository.getPorcupineApiKey()
            if (apiKey.isBlank() && BuildConfig.PORCUPINE_API_KEY.isNotBlank()) {
                apiKey = BuildConfig.PORCUPINE_API_KEY
            }
            if (apiKey.isBlank()) {
                flog.w(TAG, "Porcupine API key not set — wake word disabled")
                isWakeWordInitialized.set(false)
                return
            }

            wakeWordManager?.stop()
            val sensitivity = settingsRepository.getWakeWordSensitivitySync()
            wakeWordManager = wakeWordManagerFactory.create(
                context = this@DuqListenerService,
                accessKey = apiKey,
                sensitivity = sensitivity,
                onWakeWordDetected = { onWakeWordDetected() },
                onError = { msg ->
                    flog.e(TAG, "Wake word error: $msg")
                    _error.value = if (msg.contains("API key", ignoreCase = true))
                        DuqError.WakeWordError.invalidApiKey()
                    else DuqError.WakeWordError.initFailed(RuntimeException(msg))
                    isWakeWordInitialized.set(false)
                }
            )
            wakeWordManager?.start()
            _state.value = DuqState.IDLE
            flog.i(TAG, "Wake word active (listening for 'Hey Duq', sensitivity=$sensitivity)")
        } catch (e: Exception) {
            flog.e(TAG, "Wake word init failed: ${e.message}", e)
            _error.value = DuqError.WakeWordError.initFailed(e)
            isWakeWordInitialized.set(false)
        }
    }

    private fun onWakeWordDetected() {
        flog.i(TAG, "🦆 HEY DUQ detected — starting voice capture")
        serviceScope.launch {
            wakeWordManager?.stop(); wakeWordManager = null
            voiceCommandProcessor.processVoiceCommand(stateCallback)
            isWakeWordInitialized.set(false)
            startListening() // restart wake word after command
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "SERVICE DESTROYING")
        wakeWordManager?.stop(); wakeWordManager = null
        voiceCommandProcessor.stopRecording()
        voiceCommandProcessor.releasePlayer()
        locationReporter.stop()
        gatewayClient.stop()
        nodeClient.stop()
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }
}
