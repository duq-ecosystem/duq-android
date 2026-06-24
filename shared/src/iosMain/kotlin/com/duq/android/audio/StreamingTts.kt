package com.duq.android.audio

import com.duq.android.logging.Logger

/**
 * iOS-реализация [StreamingTtsController] — деградация-no-op на старте.
 *
 * Потоковый TTS-догон работает только с on-device-синтезом (sherpa-onnx), которого на iOS на
 * старте нет (LocalTts.isReady=false). Поэтому догон не стартует; ответ озвучивается серверным
 * /tts целиком (обычный speakReply). Все методы — no-op, [isStreaming]/[isPlaying] = false.
 */
class StreamingTts(
    private val logger: Logger,
) : StreamingTtsController {

    override fun isStreaming(runId: String): Boolean = false
    override fun isPlaying(): Boolean = false

    override fun start(runId: String) {
        logger.d(TAG, "потоковый TTS-догон не поддерживается на iOS (старт) — серверный /tts озвучит ответ")
    }

    override fun feed(runId: String, cumulativeRaw: String) {}
    override fun finish(runId: String, fullTextRaw: String) {}
    override fun cancel() {}

    private companion object {
        const val TAG = "StreamingTts"
    }
}
