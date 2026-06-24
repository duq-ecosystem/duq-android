package com.duq.android.audio

/**
 * Потоковый TTS-догон: по мере стрима текста синтезирует завершённые фразы on-device и
 * льёт их в один непрерывный аудио-выход по мере готовности (звук с первой фразы).
 *
 * Интерфейс — общий код KMP (commonMain); реализация платформенная (androidMain:
 * sherpa-onnx → AudioTrack; iosMain: деградация-no-op — догон не стартует, обычный
 * серверный speakReply озвучит ответ целиком).
 *
 * VM зовёт: start (начало голосового тёрна) → feed (на каждую дельту) → finish (финал),
 * либо cancel (abort/новый тёрн).
 */
interface StreamingTtsController {
    /** Идёт ли догон для данного runId. */
    fun isStreaming(runId: String): Boolean

    /** Озвучивает ли догон прямо сейчас (живой выход) — для кнопки-стоп в UI. */
    fun isPlaying(): Boolean

    /** Старт догона для голосового тёрна. Идемпотентно для того же runId. */
    fun start(runId: String)

    /** Скормить кумулятивный (СЫРОЙ) текст дельты: новые завершённые фразы → в синтез. */
    fun feed(runId: String, cumulativeRaw: String)

    /** Финал: остаток текста → в синтез, закрыть стрим (consumer доиграет и кэширует replay). */
    fun finish(runId: String, fullTextRaw: String)

    /** Прервать догон (новый тёрн/abort/error). */
    fun cancel()
}
