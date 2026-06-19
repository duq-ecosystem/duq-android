package com.duq.android.audio

interface VoiceActivityDetectorInterface {
    fun startRecording()
    fun stopRecording()
    fun processAudioBuffer(buffer: ShortArray, readSize: Int): Boolean
    fun reset()
}
