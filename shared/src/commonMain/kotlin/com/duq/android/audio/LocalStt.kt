package com.duq.android.audio

/**
 * On-device STT (whisper.cpp). Интерфейс — общий код KMP (commonMain);
 * реализация платформенная (androidMain: whisper.cpp JNI; iosMain: деградация —
 * tryTranscribe возвращает null → вызывающий уходит на серверный /stt).
 *
 * Путь к аудио — [String] (File недоступен в commonMain). Сигнатура [tryTranscribe]
 * сохранена 1:1 по семантике с референсным `WhisperLocal.tryTranscribe`
 * (см. DuqChatClient: `whisper.tryTranscribe(file) ?: transcribeAudioOnServer(file)`).
 */
interface LocalStt {
    /**
     * Единая точка on-device распознавания. Возвращает текст ИЛИ null — если on-device
     * STT выключен, модель не готова (и не докачалась) либо распознавание упало/пусто.
     * На null вызывающий уходит на серверный /stt.
     */
    suspend fun tryTranscribe(wavPath: String): String?

    /** Качает модель, если её ещё нет. true — модель на месте. */
    suspend fun ensureModel(onProgress: (Float) -> Unit = {}): Boolean

    /** Готова ли модель локально (без скачивания). */
    fun isModelReady(): Boolean

    /** Выгружает модель из памяти (зовётся при нехватке RAM). */
    fun release()
}
