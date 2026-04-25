package com.duq.android.service

import android.content.Context
import android.util.Base64
import android.util.Log
import com.duq.android.DuqState
import com.duq.android.audio.AudioPlayerInterface
import com.duq.android.audio.AudioRecorderInterface
import com.duq.android.audio.BeepPlayer
import com.duq.android.config.AppConfig
import com.duq.android.data.SettingsRepository
import com.duq.android.error.DuqError
import com.duq.android.network.DuqApiClient
import com.duq.android.network.DuqWebSocketClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

/**
 * Orchestrates voice command processing pipeline.
 * Delegates specific tasks to specialized components (SRP).
 */
class VoiceCommandProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorderInterface,
    private val audioPlayer: AudioPlayerInterface,
    private val apiClient: DuqApiClient,
    private val webSocketClient: DuqWebSocketClient,
    private val settingsRepository: SettingsRepository,
    private val beepPlayer: BeepPlayer,
    private val conversationUpdater: ConversationUpdater,
    private val errorMapper: ErrorMapper
) {
    companion object {
        private const val TAG = "VoiceCommandProcessor"
    }

    sealed class ProcessingResult {
        object Success : ProcessingResult()
        data class Error(val error: DuqError) : ProcessingResult()
        object RecordingFailed : ProcessingResult()
    }

    interface StateCallback {
        fun onStateChanged(state: DuqState)
        fun onError(error: DuqError)
    }

    suspend fun processVoiceCommand(callback: StateCallback): ProcessingResult {
        val audioFile = File(context.cacheDir, AppConfig.AUDIO_TEMP_FILENAME)

        return run processCommand@ {
            try {
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "🎙️ VOICE COMMAND PROCESSING START")

                // Play beep to indicate listening started
                beepPlayer.playListeningBeep()

                callback.onStateChanged(DuqState.LISTENING)
                Log.d(TAG, "→ STATE: LISTENING")

                // Record audio
                val recordResult = recordAudio(audioFile)
                if (recordResult != null) {
                    return recordResult
                }

                callback.onStateChanged(DuqState.PROCESSING)
                Log.d(TAG, "→ STATE: PROCESSING")

                // Get auth credentials
                val authToken = settingsRepository.accessToken.first()
                val userId = settingsRepository.userSub.first()

                // Ensure WebSocket connection
                if (!ensureWebSocketConnected()) {
                    return@processCommand processWithPolling(authToken, userId, audioFile, callback)
                }

                // Queue command and wait for response
                return@processCommand queueAndWaitForResponse(authToken, userId, audioFile, callback)

            } catch (e: Exception) {
                Log.e(TAG, "❌ EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
                val error = errorMapper.mapException(e)
                callback.onError(error)
                ProcessingResult.Error(error)
            } finally {
                cleanupAudioFile(audioFile)
            }
        }
    }

    private suspend fun recordAudio(audioFile: File): ProcessingResult? {
        Log.d(TAG, "Recording to: ${audioFile.absolutePath}")

        val recordingStartTime = System.currentTimeMillis()
        val success = audioRecorder.recordUntilSilence(audioFile)
        val recordingDuration = System.currentTimeMillis() - recordingStartTime

        if (!success || !audioFile.exists() || audioFile.length() == 0L) {
            Log.e(TAG, "❌ RECORDING FAILED: success=$success, exists=${audioFile.exists()}, size=${audioFile.length()}")
            return ProcessingResult.RecordingFailed
        }

        Log.d(TAG, "✅ Recording: ${recordingDuration}ms, ${audioFile.length() / 1024}KB")
        return null
    }

    private suspend fun ensureWebSocketConnected(): Boolean {
        if (webSocketClient.isConnected()) return true

        Log.d(TAG, "WebSocket not connected, connecting...")
        webSocketClient.connect()

        withTimeoutOrNull(AppConfig.WS_CONNECT_TIMEOUT_MS) {
            webSocketClient.connectionState
                .filter { it == DuqWebSocketClient.ConnectionState.Connected }
                .first()
        }

        return webSocketClient.isConnected().also {
            if (!it) Log.e(TAG, "❌ WebSocket connection failed")
        }
    }

    private suspend fun queueAndWaitForResponse(
        authToken: String,
        userId: String,
        audioFile: File,
        callback: StateCallback
    ): ProcessingResult {
        val sendResult = apiClient.queueVoiceCommand(authToken, audioFile, userId)

        return when (sendResult) {
            is DuqApiClient.SendResult.Queued -> {
                Log.d(TAG, "✅ Task queued: ${sendResult.taskId}")
                waitForWebSocketResponse(authToken, sendResult.taskId, callback)
            }
            is DuqApiClient.SendResult.Error -> {
                Log.e(TAG, "❌ Queue error: ${sendResult.message}")
                val error = errorMapper.mapApiError(sendResult)
                callback.onError(error)
                ProcessingResult.Error(error)
            }
        }
    }

    private suspend fun waitForWebSocketResponse(
        authToken: String,
        taskId: String,
        callback: StateCallback
    ): ProcessingResult {
        val response = withTimeoutOrNull(AppConfig.WS_RESPONSE_TIMEOUT_MS) {
            webSocketClient.messages
                .filter { it.taskId == taskId }
                .first()
        }

        if (response == null) {
            Log.e(TAG, "❌ WebSocket timeout, polling for task")
            return pollForTaskResult(authToken, taskId, callback)
        }

        Log.d(TAG, "✅ WebSocket response: type=${response.type}")

        return when (response.type) {
            "response", "success" -> handleSuccessResponse(authToken, response, callback)
            "error" -> {
                val error = DuqError.NetworkError(response.error ?: "Unknown error")
                callback.onError(error)
                ProcessingResult.Error(error)
            }
            else -> {
                Log.w(TAG, "Unexpected response type: ${response.type}")
                ProcessingResult.Success
            }
        }
    }

    private suspend fun handleSuccessResponse(
        authToken: String,
        response: DuqWebSocketClient.WSMessage,
        callback: StateCallback
    ): ProcessingResult {
        val audioData = response.voiceData?.let { Base64.decode(it, Base64.DEFAULT) } ?: ByteArray(0)

        if (audioData.isNotEmpty()) {
            callback.onStateChanged(DuqState.PLAYING)
            audioPlayer.playAudio(audioData)
            Log.d(TAG, "✅ Playback complete")
        }

        conversationUpdater.refreshConversation(authToken)

        Log.d(TAG, "🏁 VOICE COMMAND COMPLETE")
        return ProcessingResult.Success
    }

    private suspend fun processWithPolling(
        authToken: String,
        userId: String,
        audioFile: File,
        callback: StateCallback
    ): ProcessingResult {
        Log.d(TAG, "Using polling fallback...")

        @Suppress("DEPRECATION")
        val result = apiClient.sendVoiceCommand("", authToken, audioFile, userId)

        return handleApiResult(result, callback, "Polling")
    }

    private suspend fun pollForTaskResult(
        authToken: String,
        taskId: String,
        callback: StateCallback
    ): ProcessingResult {
        Log.d(TAG, "Polling for task: $taskId")
        val result = apiClient.pollForTask(authToken, taskId)
        return handleApiResult(result, callback, "Poll fallback")
    }

    private suspend fun handleApiResult(
        result: DuqApiClient.ApiResult,
        callback: StateCallback,
        source: String
    ): ProcessingResult {
        return when (result) {
            is DuqApiClient.ApiResult.Success -> {
                Log.d(TAG, "✅ $source SUCCESS: ${result.audioData.size} bytes")
                if (result.audioData.isNotEmpty()) {
                    callback.onStateChanged(DuqState.PLAYING)
                    audioPlayer.playAudio(result.audioData)
                }
                Log.d(TAG, "🏁 VOICE COMMAND COMPLETE ($source)")
                ProcessingResult.Success
            }
            is DuqApiClient.ApiResult.Error -> {
                Log.e(TAG, "❌ $source ERROR: ${result.message}")
                val error = DuqError.NetworkError(result.message, result.code)
                callback.onError(error)
                ProcessingResult.Error(error)
            }
        }
    }

    private fun cleanupAudioFile(audioFile: File) {
        if (audioFile.exists()) {
            audioFile.delete()
            Log.d(TAG, "Audio file cleaned up")
        }
    }

    fun stopRecording() = audioRecorder.stopRecording()
    fun initializePlayer() = audioPlayer.initialize()
    fun releasePlayer() = audioPlayer.release()
}
