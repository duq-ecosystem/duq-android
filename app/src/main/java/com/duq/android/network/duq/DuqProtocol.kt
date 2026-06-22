package com.duq.android.network.duq

import com.google.gson.annotations.SerializedName

/**
 * DTO нового контракта ядра DUQ (собственное Python-ядро duq-core за nginx,
 * домен on-za-menya.online, префикс /duq). Заменяет прежний протокол чата.
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

/**
 * Live-сообщение беседы, пришедшее пушем по /duq/ws (полный синк API↔мобилка):
 * ответ бота, проактив/morning-brief, или сообщение, отправленное через REST из
 * другого источника. messageId — серверный id (для идемпотентности/дедупа).
 */
data class DuqIncomingMessage(
    val messageId: String,
    val role: String,    // "user" | "assistant"
    val content: String
)

// ── Чат-события / состояние соединения (перенесено из удалённого легаси-слоя) ──

/** Терминальное (или стрим-) событие ответа, которое рендерит ConversationViewModel. */
data class OcChatEvent(
    val runId: String,
    val sessionKey: String,
    val seq: Int,
    val state: String,          // "delta" | "final" | "error" | "aborted"
    val deltaText: String? = null,
    val fullText: String? = null,
    val errorMessage: String? = null,
    val stopReason: String? = null
)

/** Шаг агента (tool/command) внутри ответа — пока ядром не передаётся (заглушка). */
data class OcAgentStep(
    val runId: String,
    val itemId: String,
    val kind: String,    // "tool" | "command"
    val title: String,
    val status: String,  // "running" | "completed" | "failed"
    val phase: String    // "update" | "end"
)

/** Одно прошлое сообщение из истории беседы (role/text), render-ready. */
data class OcHistoryMsg(
    val role: String,  // "user" | "assistant"
    val text: String
)

/** Состояние WS-соединения чат-слоя. */
enum class GatewayConnectionState { DISCONNECTED, CONNECTING, CONNECTED, PAIRING, ERROR }
