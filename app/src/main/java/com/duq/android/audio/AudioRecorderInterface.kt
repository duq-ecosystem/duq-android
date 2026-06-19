package com.duq.android.audio

import java.io.File

interface AudioRecorderInterface {
    /**
     * Records mic audio to [outputFile] as WAV.
     *
     * @param useVad when true, recording stops automatically once the VAD detects
     *   end-of-speech silence (hands-free flow). When false, recording continues
     *   until [stopRecording] is called — the push-to-talk (hold-to-talk) flow,
     *   where the user controls the endpoint and natural pauses must NOT cut it off.
     * @return true if a non-empty recording was captured.
     */
    suspend fun record(outputFile: File, useVad: Boolean): Boolean

    /** Stops an in-progress recording (push-to-talk release, or cancel). */
    fun stopRecording()
}
