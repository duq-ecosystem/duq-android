package com.jarvis.android.audio

import java.io.File

interface AudioRecorderInterface {
    suspend fun recordUntilSilence(outputFile: File): Boolean
    fun stopRecording()
}
