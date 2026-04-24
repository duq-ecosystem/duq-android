package com.duq.android.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Base64
import android.util.Log
import com.duq.android.DuqState
import com.duq.android.audio.AudioPlayerInterface
import com.duq.android.error.DuqError
import com.duq.android.audio.AudioRecorderInterface
import com.duq.android.data.ConversationRepository
import com.duq.android.data.SettingsRepository
import com.duq.android.network.DuqApiClient
import com.duq.android.network.DuqWebSocketClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

class VoiceCommandProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorderInterface,
    private val audioPlayer: AudioPlayerInterface,
    private val apiClient: DuqApiClient,
    private val webSocketClient: DuqWebSocketClient,
    private val settingsRepository: SettingsRepository,
    private val conversationRepository: ConversationRepository
) {
    companion object {
        private const val TAG = "VoiceCommandProcessor"
        private const val WS_RESPONSE_TIMEOUT_MS = 60_000L  // 60s timeout for WebSocket response
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

    private suspend fun playListeningBeep() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            delay(200)
            toneGenerator.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play beep", e)
        }
    }

    suspend fun processVoiceCommand(callback: StateCallback): ProcessingResult {
        val audioFile = File(context.cacheDir, "voice_command.wav")

        return run processCommand@ {
            try {
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "🎙️ VOICE COMMAND PROCESSING START")

            // Play beep to indicate listening started
            Log.d(TAG, "Playing listening beep...")
            playListeningBeep()
            Log.d(TAG, "Beep completed")

            Log.d(TAG, "→ STATE: LISTENING")
            callback.onStateChanged(DuqState.LISTENING)

            Log.d(TAG, "Recording to: ${audioFile.absolutePath}")

            val recordingStartTime = System.currentTimeMillis()
            val success = audioRecorder.recordUntilSilence(audioFile)
            val recordingDuration = System.currentTimeMillis() - recordingStartTime

            if (!success || !audioFile.exists() || audioFile.length() == 0L) {
                Log.e(TAG, "❌ RECORDING FAILED")
                Log.e(TAG, "Success: $success, Exists: ${audioFile.exists()}, Size: ${audioFile.length()}")
                Log.e(TAG, "═══════════════════════════════════════")
                return ProcessingResult.RecordingFailed
            }

            Log.d(TAG, "✅ Recording complete")
            Log.d(TAG, "Duration: ${recordingDuration}ms")
            Log.d(TAG, "File size: ${audioFile.length()} bytes (${audioFile.length() / 1024}KB)")

            Log.d(TAG, "→ STATE: PROCESSING")
            callback.onStateChanged(DuqState.PROCESSING)

            Log.d(TAG, "Getting access token and user ID...")
            val authToken = settingsRepository.accessToken.first()
            val userId = settingsRepository.userSub.first()
            Log.d(TAG, "Token retrieved: ${authToken.take(20)}...")
            Log.d(TAG, "User ID: $userId")

            // Ensure WebSocket is connected
            if (!webSocketClient.isConnected()) {
                Log.d(TAG, "WebSocket not connected, connecting...")
                webSocketClient.connect()
                // Wait for connection (max 5s)
                withTimeoutOrNull(5000) {
                    webSocketClient.connectionState
                        .filter { it == DuqWebSocketClient.ConnectionState.Connected }
                        .first()
                }
                if (!webSocketClient.isConnected()) {
                    Log.e(TAG, "❌ Failed to connect WebSocket, falling back to polling")
                    return@processCommand processWithPolling(authToken, userId, audioFile, callback)
                }
            }

            Log.d(TAG, "Queueing voice command via API...")
            val sendResult = apiClient.queueVoiceCommand(authToken, audioFile, userId)

            when (sendResult) {
                is DuqApiClient.SendResult.Queued -> {
                    Log.d(TAG, "✅ Task queued: ${sendResult.taskId}")
                    Log.d(TAG, "Waiting for WebSocket response...")

                    // Wait for response via WebSocket
                    val response = withTimeoutOrNull(WS_RESPONSE_TIMEOUT_MS) {
                        webSocketClient.messages
                            .filter { it.taskId == sendResult.taskId }
                            .first()
                    }

                    if (response == null) {
                        // Task was already queued - poll for result instead of re-sending
                        Log.e(TAG, "❌ WebSocket response timeout, polling for task result")
                        return@processCommand pollForTaskResult(authToken, sendResult.taskId, callback)
                    }

                    Log.d(TAG, "✅ WebSocket response received: type=${response.type}")

                    when (response.type) {
                        "response", "success" -> {
                            val text = response.text ?: ""
                            val audioData = response.voiceData?.let {
                                Base64.decode(it, Base64.DEFAULT)
                            } ?: ByteArray(0)

                            Log.d(TAG, "Response text: ${text.take(100)}${if (text.length > 100) "..." else ""}")
                            Log.d(TAG, "Audio: ${audioData.size} bytes")

                            if (audioData.isNotEmpty()) {
                                Log.d(TAG, "→ STATE: PLAYING")
                                callback.onStateChanged(DuqState.PLAYING)

                                Log.d(TAG, "Starting audio playback...")
                                val playbackSuccess = audioPlayer.playAudio(audioData)

                                if (playbackSuccess) {
                                    Log.d(TAG, "✅ Playback completed successfully")
                                } else {
                                    Log.w(TAG, "⚠️ Playback completed with warning")
                                }
                            } else {
                                Log.d(TAG, "No audio data, skipping playback")
                            }

                            // Update conversation history from backend
                            try {
                                Log.d(TAG, "Refreshing conversation history from backend...")
                                val conversationId = conversationRepository.getCurrentConversationId(authToken)
                                if (conversationId != null) {
                                    conversationRepository.refreshMessages(authToken, conversationId)
                                    Log.d(TAG, "✅ Conversation history refreshed")
                                } else {
                                    Log.w(TAG, "⚠️ No active conversation to refresh")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Failed to refresh conversation: ${e.message}")
                            }

                            Log.d(TAG, "🏁 VOICE COMMAND PROCESSING COMPLETE (WebSocket)")
                            Log.d(TAG, "═══════════════════════════════════════")
                            ProcessingResult.Success
                        }
                        "error" -> {
                            val errorMsg = response.error ?: "Unknown error"
                            Log.e(TAG, "❌ WebSocket error response: $errorMsg")
                            Log.e(TAG, "═══════════════════════════════════════")
                            val error = DuqError.NetworkError(errorMsg)
                            callback.onError(error)
                            ProcessingResult.Error(error)
                        }
                        else -> {
                            Log.w(TAG, "Unexpected response type: ${response.type}")
                            ProcessingResult.Success
                        }
                    }
                }
                is DuqApiClient.SendResult.Error -> {
                    Log.e(TAG, "❌ Queue error: ${sendResult.message}")
                    Log.e(TAG, "═══════════════════════════════════════")
                    val error = when {
                        sendResult.code == 401 || sendResult.code == 403 -> DuqError.AuthError.invalidToken()
                        sendResult.code != null && sendResult.code >= 500 -> DuqError.NetworkError.serverError(sendResult.code, sendResult.message)
                        sendResult.code != null -> DuqError.NetworkError.clientError(sendResult.code, sendResult.message)
                        else -> DuqError.NetworkError(sendResult.message, sendResult.code)
                    }
                    callback.onError(error)
                    ProcessingResult.Error(error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION IN VOICE COMMAND PROCESSING")
            Log.e(TAG, "Error: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            Log.e(TAG, "═══════════════════════════════════════")
            val error = when (e) {
                is java.net.SocketTimeoutException -> DuqError.NetworkError.timeout()
                is java.net.UnknownHostException -> DuqError.NetworkError.noConnection()
                is java.net.ConnectException -> DuqError.NetworkError.noConnection("Connection refused: ${e.message}")
                is java.io.IOException -> DuqError.NetworkError(e.message ?: "Network error")
                else -> DuqError.AudioError(e.message ?: "Unknown error", e)
            }
            callback.onError(error)
            ProcessingResult.Error(error)
            } finally {
                // Always clean up audio file to prevent file descriptor leaks
                if (audioFile.exists()) {
                    Log.d(TAG, "Cleaning up audio file...")
                    audioFile.delete()
                }
            }
        }
    }

    /**
     * Fallback: process voice command using polling instead of WebSocket
     */
    private suspend fun processWithPolling(
        authToken: String,
        userId: String,
        audioFile: File,
        callback: StateCallback
    ): ProcessingResult {
        Log.d(TAG, "Using polling fallback...")

        @Suppress("DEPRECATION")
        val result = apiClient.sendVoiceCommand("", authToken, audioFile, userId)

        return when (result) {
            is DuqApiClient.ApiResult.Success -> {
                Log.d(TAG, "✅ Polling SUCCESS")
                Log.d(TAG, "Response audio: ${result.audioData.size} bytes")

                if (result.audioData.isNotEmpty()) {
                    Log.d(TAG, "→ STATE: PLAYING")
                    callback.onStateChanged(DuqState.PLAYING)
                    audioPlayer.playAudio(result.audioData)
                }

                Log.d(TAG, "🏁 VOICE COMMAND COMPLETE (Polling)")
                Log.d(TAG, "═══════════════════════════════════════")
                ProcessingResult.Success
            }
            is DuqApiClient.ApiResult.Error -> {
                Log.e(TAG, "❌ Polling ERROR: ${result.message}")
                val error = DuqError.NetworkError(result.message, result.code)
                callback.onError(error)
                ProcessingResult.Error(error)
            }
        }
    }

    /**
     * Poll for task result (used when WebSocket times out but task was already queued)
     */
    private suspend fun pollForTaskResult(
        authToken: String,
        taskId: String,
        callback: StateCallback
    ): ProcessingResult {
        Log.d(TAG, "Polling for task result: $taskId")

        val result = apiClient.pollForTask(authToken, taskId)

        return when (result) {
            is DuqApiClient.ApiResult.Success -> {
                Log.d(TAG, "✅ Poll SUCCESS")
                Log.d(TAG, "Response audio: ${result.audioData.size} bytes")

                if (result.audioData.isNotEmpty()) {
                    Log.d(TAG, "→ STATE: PLAYING")
                    callback.onStateChanged(DuqState.PLAYING)
                    audioPlayer.playAudio(result.audioData)
                }

                Log.d(TAG, "🏁 VOICE COMMAND COMPLETE (Poll fallback)")
                Log.d(TAG, "═══════════════════════════════════════")
                ProcessingResult.Success
            }
            is DuqApiClient.ApiResult.Error -> {
                Log.e(TAG, "❌ Poll ERROR: ${result.message}")
                val error = DuqError.NetworkError(result.message, result.code)
                callback.onError(error)
                ProcessingResult.Error(error)
            }
        }
    }

    fun stopRecording() {
        audioRecorder.stopRecording()
    }

    fun initializePlayer() {
        audioPlayer.initialize()
    }

    fun releasePlayer() {
        audioPlayer.release()
    }
}
