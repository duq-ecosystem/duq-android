package com.duq.android.audio

/** PCM16-сэмплы синтеза + их частота — для потокового вывода (нормальный стриминг догона). */
data class TtsSamples(val pcm: ShortArray, val sampleRate: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TtsSamples) return false
        return sampleRate == other.sampleRate && pcm.contentEquals(other.pcm)
    }

    override fun hashCode(): Int = 31 * pcm.contentHashCode() + sampleRate
}

/**
 * On-device TTS (sherpa-onnx Piper VITS). Интерфейс — общий код KMP (commonMain);
 * реализация платформенная (androidMain: sherpa-onnx; iosMain: деградация —
 * trySynthesize/synthesizeSamples возвращают null → вызывающий уходит на серверный /tts).
 *
 * Возвращаемый путь к WAV — [String] (File недоступен в commonMain). Сигнатуры сохранены
 * по семантике с референсным `TtsLocal` (см. ConversationViewModel.speakReply:
 * `ttsLocal.trySynthesize(...) ?: ttsClient.synthesize(...)`).
 */
interface LocalTts {
    /**
     * Единая точка on-device синтеза. Возвращает путь к WAV-файлу ИЛИ null — если on-device
     * TTS выключен, модель не готова (и не докачалась) либо синтез упал. На null вызывающий
     * уходит на серверный /tts.
     */
    suspend fun trySynthesize(text: String, messageId: String): String?

    /**
     * PCM16-сэмплы фразы для НОРМАЛЬНОГО стриминга в AudioTrack (без WAV-файлов/очередей).
     * null — движок не готов (НЕ скачивает) или ошибка.
     */
    suspend fun synthesizeSamples(text: String): TtsSamples?

    /** Готов ли on-device движок (модель скачана) БЕЗ скачивания — решает, стримить ли догоном. */
    fun isReady(): Boolean

    /** Качает + распаковывает бандл модели, если его ещё нет. true — модель готова. */
    suspend fun ensureModel(onProgress: (Float) -> Unit = {}): Boolean

    /** Бандл распакован (модель + токены + espeak-данные на месте). */
    fun isModelReady(): Boolean

    /** Выгружает движок из нативной памяти (зовётся при нехватке RAM). */
    fun release()
}
