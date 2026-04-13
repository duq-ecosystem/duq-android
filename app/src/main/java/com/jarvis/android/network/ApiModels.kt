package com.jarvis.android.network

import com.google.gson.annotations.SerializedName

/**
 * API response models for backend communication
 */

data class ConversationResponse(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("title") val title: String?,
    @SerializedName("started_at") val startedAt: Long,
    @SerializedName("last_message_at") val lastMessageAt: Long,
    @SerializedName("is_active") val isActive: Boolean
)

data class MessageResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
    @SerializedName("has_audio") val hasAudio: Boolean,
    @SerializedName("audio_duration_ms") val audioDurationMs: Int?,
    @SerializedName("waveform") val waveform: List<Float>?,
    @SerializedName("created_at") val createdAt: Long
)

data class CreateConversationRequest(
    @SerializedName("title") val title: String?
)
