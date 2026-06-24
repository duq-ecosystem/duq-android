package com.duq.android.util

/**
 * Нормализует текст ответа ассистента перед показом. LIVE-стрим несёт управляющие
 * токены (NO_REPLY / HEARTBEAT_OK / SESSION_STATUS), которые ядро помечает
 * подавляемыми — клиент должен вырезать их, иначе heartbeat-ack и пр. всплывают мусором.
 */
object ReplyText {

    // Регэкспы мультиплатформенные. SESSION_STATUS может быть приклеен к тексту
    // (вкл. кириллицу) → без word-boundary.
    private val SENTINELS = listOf(
        Regex("(?i)\\bno_reply\\b"),
        Regex("(?i)\\bheartbeat_ok\\b"),
        Regex("(?i)SESSION_STATUS"),
    )

    /** Вырезает управляющие токены и тримит. */
    fun clean(text: String): String =
        SENTINELS.fold(text) { acc, re -> re.replace(acc, "") }.trim()

    /** True, если сообщение — только токены и его надо подавить. */
    fun isSuppressed(text: String): Boolean = clean(text).isEmpty()
}
