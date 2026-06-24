package com.duq.android.audio

import com.duq.android.logging.Logger

/**
 * iOS-реализация [LocalTts] — деградация на серверный TTS.
 *
 * On-device sherpa-onnx на iOS на старте не задействован; [trySynthesize]/[synthesizeSamples]
 * возвращают null → вызывающий (ConversationViewModel.speakReply) уходит на серверный /tts.
 * [isReady] = false → потоковый догон (StreamingTts) на iOS не стартует, ответ озвучивается
 * серверным синтезом целиком. Нативный TTS под iOS — отдельная итерация.
 */
class TtsLocal(
    private val logger: Logger,
) : LocalTts {

    override suspend fun trySynthesize(text: String, messageId: String): String? {
        logger.d(TAG, "on-device TTS не поддерживается на iOS (старт) — fallback на серверный /tts")
        return null
    }

    override suspend fun synthesizeSamples(text: String): TtsSamples? = null

    override fun isReady(): Boolean = false

    override suspend fun ensureModel(onProgress: (Float) -> Unit): Boolean = false

    override fun isModelReady(): Boolean = false

    override fun release() {}

    private companion object {
        const val TAG = "TtsLocal"
    }
}
