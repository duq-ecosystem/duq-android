package com.duq.android.data.model

import java.time.Instant

/**
 * Message in a conversation
 */
data class Message(
    val id: Long,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val hasAudio: Boolean = false,
    val audioDurationMs: Int? = null,
    val waveform: List<Float>? = null,
    val createdAt: Instant
)

enum class MessageRole {
    USER,
    ASSISTANT;

    fun toApiString(): String = when (this) {
        USER -> "user"
        ASSISTANT -> "assistant"
    }

    companion object {
        fun fromApiString(value: String): MessageRole = when (value.lowercase()) {
            "user" -> USER
            "assistant" -> ASSISTANT
            else -> throw IllegalArgumentException("Unknown message role: $value")
        }
    }
}
