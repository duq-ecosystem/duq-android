package com.jarvis.android.data.model

import java.time.Instant

/**
 * Conversation with Jarvis assistant
 */
data class Conversation(
    val id: String,
    val userId: Long,
    val title: String? = null,
    val startedAt: Instant,
    val lastMessageAt: Instant,
    val isActive: Boolean = true
)
