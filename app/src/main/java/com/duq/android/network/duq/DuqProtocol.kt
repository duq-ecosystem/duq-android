package com.duq.android.network.duq

import com.google.gson.annotations.SerializedName

/**
 * DTO нового контракта ядра DUQ (собственное Python-ядро duq-core за nginx,
 * домен on-za-menya.online, префикс /duq). Заменяет OpenClaw-протокол для ЧАТА.
 *
 * Контракт (Ф3a — только чат):
 *  - POST /duq/api/message            {message}            → {task_id,status}
 *  - GET  /duq/api/task/{task_id}                          → {status,result,error}
 *  - GET  /duq/api/conversations                           → [ConversationDto]
 *  - GET  /duq/api/conversations/{id}/messages             → [HistoryMsg]
 *
 * Все запросы несут edge-токен X-Auth-Token (см. network/ServerAuth.withServerAuth()).
 */

/** Тело POST /duq/api/message. */
data class MessageRequest(
    val message: String
)

/** Ответ POST /duq/api/message — задача поставлена в очередь. */
data class MessageEnqueued(
    @SerializedName("task_id") val taskId: String,
    val status: String
)

/**
 * Ответ GET /duq/api/task/{task_id}. Поллится до status == "completed"
 * (терминал) либо "failed"/непустой [error]. Пока "running"/"pending" — ждём.
 */
data class TaskResult(
    val status: String,
    val result: TaskResponse? = null,
    val error: String? = null
) {
    val isCompleted: Boolean get() = status.equals("completed", ignoreCase = true)
    val isFailed: Boolean
        get() = status.equals("failed", ignoreCase = true) || !error.isNullOrBlank()
    val isTerminal: Boolean get() = isCompleted || isFailed
}

/** Полезная нагрузка завершённой задачи. */
data class TaskResponse(
    val response: String? = null,
    val channel: String? = null
)

/** Один диалог из GET /duq/api/conversations. */
data class ConversationDto(
    val id: String,
    val title: String? = null
)

/** Одно сообщение из GET /duq/api/conversations/{id}/messages. */
data class HistoryMsg(
    val role: String,    // "user" | "assistant"
    val content: String
)
