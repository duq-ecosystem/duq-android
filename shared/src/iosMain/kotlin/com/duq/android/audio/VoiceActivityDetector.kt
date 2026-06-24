package com.duq.android.audio

/**
 * iOS-реализация [VoiceActivityDetectorInterface] — no-op.
 *
 * VAD нужен только для hands-free авто-отсечки тишины при on-device записи (Android). На iOS
 * запись на старте идёт через серверный push-to-talk без авто-эндпоинта, поэтому VAD не
 * задействован: [processAudioBuffer] всегда false (никогда не сигналит «конец речи»).
 */
class IosVoiceActivityDetector : VoiceActivityDetectorInterface {
    override fun startRecording() {}
    override fun stopRecording() {}
    override fun processAudioBuffer(buffer: ShortArray, readSize: Int): Boolean = false
    override fun reset() {}
}
