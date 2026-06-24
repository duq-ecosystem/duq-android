package com.duq.android.audio

/**
 * Запись микрофона в WAV (16 kHz mono PCM16). Интерфейс — общий код KMP (commonMain);
 * реализация платформенная (androidMain: AudioRecord; iosMain: деградация на серверный /stt).
 *
 * Путь к файлу — [String] (а не java.io.File, недоступный в commonMain): платформа сама
 * резолвит абсолютный путь во временной директории.
 */
interface AudioRecorderInterface {
    /**
     * Records mic audio to [outputPath] as WAV.
     *
     * @param useVad when true, recording stops automatically once the VAD detects
     *   end-of-speech silence (hands-free flow). When false, recording continues
     *   until [stopRecording] is called — the push-to-talk (hold-to-talk) flow,
     *   where the user controls the endpoint and natural pauses must NOT cut it off.
     * @return true if a non-empty recording was captured.
     */
    suspend fun record(outputPath: String, useVad: Boolean): Boolean

    /** Stops an in-progress recording (push-to-talk release, or cancel). */
    fun stopRecording()
}
