package com.duq.android.service

import android.content.Context
import android.util.Log
import com.duq.android.DuqState
import com.duq.android.audio.AudioRecorderInterface
import com.duq.android.audio.BeepPlayer
import com.duq.android.audio.ChatAudioPlaybackManager
import com.duq.android.config.AppConfig
import com.duq.android.error.DuqError
import com.duq.android.network.openclaw.OpenClawGatewayClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceCommandProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorderInterface,
    private val chatAudioPlaybackManager: ChatAudioPlaybackManager,
    private val beepPlayer: BeepPlayer,
    private val gatewayClient: OpenClawGatewayClient,
    private val errorMapper: ErrorMapper
) {
    companion object { private const val TAG = "VoiceProcessor" }

    sealed class ProcessingResult {
        object Success : ProcessingResult()
        data class Error(val error: DuqError) : ProcessingResult()
        object RecordingFailed : ProcessingResult()
    }

    interface StateCallback {
        fun onStateChanged(state: DuqState)
        fun onError(error: DuqError)
    }

    private var isPlayerInitialized = false

    fun initializePlayer() {
        if (!isPlayerInitialized) { chatAudioPlaybackManager.initialize(); isPlayerInitialized = true }
    }

    fun releasePlayer() {
        if (isPlayerInitialized) { chatAudioPlaybackManager.release(); isPlayerInitialized = false }
    }

    fun stopRecording() = audioRecorder.stopRecording()

    suspend fun processVoiceCommand(callback: StateCallback): ProcessingResult {
        val audioFile = File(context.cacheDir, AppConfig.AUDIO_TEMP_FILENAME)
        return try {
            beepPlayer.playListeningBeep()
            callback.onStateChanged(DuqState.LISTENING)

            val recorded = audioRecorder.record(audioFile, useVad = true)
            if (!recorded) {
                Log.w(TAG, "Recording too short or failed")
                callback.onStateChanged(DuqState.IDLE)
                return ProcessingResult.RecordingFailed
            }

            callback.onStateChanged(DuqState.PROCESSING)

            // STT
            val transcript = try {
                gatewayClient.transcribeAudio(audioFile)
            } catch (e: Exception) {
                Log.e(TAG, "STT failed: ${e.message}")
                val err = DuqError.NetworkError(e.message ?: "STT failed")
                callback.onError(err)
                return ProcessingResult.Error(err)
            }

            if (transcript.isBlank()) {
                callback.onStateChanged(DuqState.IDLE)
                return ProcessingResult.RecordingFailed
            }

            Log.d(TAG, "Transcript: $transcript")
            gatewayClient.sendMessage(transcript)
            callback.onStateChanged(DuqState.IDLE)
            ProcessingResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Voice processing failed: ${e.message}")
            val err = errorMapper.mapException(e)
            callback.onError(err)
            callback.onStateChanged(DuqState.ERROR)
            ProcessingResult.Error(err)
        } finally {
            audioFile.delete()
        }
    }
}
