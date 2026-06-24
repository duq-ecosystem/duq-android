package com.duq.android.audio

import com.duq.android.logging.Logger

/**
 * iOS-реализация [AudioRecorderInterface] — деградация на старте.
 *
 * Запись микрофона на iOS на старте идёт через серверный голосовой путь (push-to-talk →
 * /stt), нативная AVAudioEngine-запись — отдельная итерация. Сейчас [record] не захватывает
 * звук и честно возвращает false (вызывающий обработает «нет записи»), с логом-деградацией.
 */
class IosAudioRecorder(
    private val logger: Logger,
) : AudioRecorderInterface {

    override suspend fun record(outputPath: String, useVad: Boolean): Boolean {
        logger.w(TAG, "запись микрофона не поддерживается на iOS (старт) — голос идёт через сервер")
        return false
    }

    override fun stopRecording() {
        // нет активной записи на iOS — no-op
    }

    private companion object {
        const val TAG = "AudioRecorder"
    }
}
