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

/**
 * Тело POST /duq/api/message.
 *
 * [conversationId] — адресная отправка в КОНКРЕТНУЮ беседу (переключатель диалогов):
 * история и сохранение идут в неё, а не в активную. null → активная беседа (как было).
 * [newConversation] — начать новый диалог (деактивирует текущий активный). Оба поля
 * Gson опускает при null, так что прежний контракт ({message}) не меняется.
 */
data class MessageRequest(
    val message: String,
    @SerializedName("conversation_id") val conversationId: String? = null,
    @SerializedName("new_conversation") val newConversation: Boolean? = null
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

/** Один диалог из GET /duq/api/conversations (отсортированы по last_message_at DESC). */
data class ConversationDto(
    val id: String,
    val title: String? = null,
    @SerializedName("last_message_at") val lastMessageAt: Long = 0,
    @SerializedName("started_at") val startedAt: Long = 0,
    @SerializedName("is_active") val isActive: Boolean = false
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
    val content: String,
    // Беседа, к которой относится сообщение (для фильтрации по активному диалогу при
    // переключении). null — старый формат пуша без conversation_id.
    val conversationId: String? = null
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

/**
 * Беседа для переключателя диалогов, render-ready.
 *  - [dateLabel] — человеческая русская дата (Сегодня/Вчера/«20 июня») для КНОПКИ-шапки.
 *  - [summary]   — краткое саммари темы (как в ChatGPT/Claude) для списка истории;
 *                  null, если беседа ещё не оттайтлена (тогда в списке показываем дату).
 */
data class DuqConversation(
    val id: String,
    val summary: String?,
    val dateLabel: String,
    val lastMessageAt: Long,
    val isActive: Boolean
)

/** Состояние WS-соединения чат-слоя. */
enum class GatewayConnectionState { DISCONNECTED, CONNECTING, CONNECTED, PAIRING, ERROR }
