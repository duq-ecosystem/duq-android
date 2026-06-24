package com.duq.android.audio

/**
 * Детектор речевой активности (VAD). Интерфейс — общий код KMP (commonMain);
 * реализация платформенная (androidMain: Silero DNN; iosMain: no-op — VAD не нужен,
 * iOS-голос на старте идёт через серверный STT/push-to-talk без авто-отсечки).
 */
interface VoiceActivityDetectorInterface {
    fun startRecording()
    fun stopRecording()
    fun processAudioBuffer(buffer: ShortArray, readSize: Int): Boolean
    fun reset()
}
