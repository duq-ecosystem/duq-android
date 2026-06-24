package com.duq.android.audio

import com.duq.android.logging.Logger

/**
 * iOS-реализация [LocalStt] — деградация на серверный STT.
 *
 * On-device whisper.cpp на iOS на старте не задействован; [tryTranscribe] возвращает null →
 * вызывающий (DuqChatClient) уходит на серверный /stt. Это штатный путь для iOS-голоса на
 * старте (см. ConversationViewModel/DuqChatClient). Нативный whisper под iOS — отдельная итерация.
 */
class WhisperLocal(
    private val logger: Logger,
) : LocalStt {

    override suspend fun tryTranscribe(wavPath: String): String? {
        logger.d(TAG, "on-device STT не поддерживается на iOS (старт) — fallback на серверный /stt")
        return null
    }

    override suspend fun ensureModel(onProgress: (Float) -> Unit): Boolean = false

    override fun isModelReady(): Boolean = false

    override fun release() {}

    private companion object {
        const val TAG = "WhisperLocal"
    }
}
