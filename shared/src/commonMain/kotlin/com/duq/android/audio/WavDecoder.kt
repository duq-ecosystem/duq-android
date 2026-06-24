package com.duq.android.audio

/**
 * Разбор WAV-контейнера в float32 PCM. Чистая утилита формата — отдельно от STT-движка
 * (`WhisperLocal`), чтобы тот не смешивал распознавание с парсингом аудио (SRP).
 *
 * Чистый Kotlin stdlib (общий код KMP, commonMain): на вход — сырые байты файла
 * (платформа сама читает их с диска: Android из File, iOS из NSData), на выход — float32.
 */
object WavDecoder {
    private const val WAV_HEADER_BYTES = 44

    /** WAV 16 kHz mono PCM16 → float32 [-1,1]. Пропускает 44-байтный заголовок. */
    fun decodePcm16Mono(bytes: ByteArray): FloatArray {
        if (bytes.size <= WAV_HEADER_BYTES) return FloatArray(0)
        val sampleCount = (bytes.size - WAV_HEADER_BYTES) / 2
        val out = FloatArray(sampleCount)
        var pos = WAV_HEADER_BYTES
        var i = 0
        while (i < sampleCount) {
            // little-endian PCM16 → знаковый short → float
            val lo = bytes[pos].toInt() and 0xFF
            val hi = bytes[pos + 1].toInt() // знаковый старший байт
            val sample = (hi shl 8) or lo
            out[i] = sample / 32768.0f
            pos += 2
            i++
        }
        return out
    }
}
