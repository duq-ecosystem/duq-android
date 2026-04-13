package com.duq.android.data.model

import java.time.Instant

/**
 * Conversation with Duq assistant
 */
data class Conversation(
    val id: String,
    val userId: Long,
    val title: String? = null,
    val startedAt: Instant,
    val lastMessageAt: Instant,
    val isActive: Boolean = true
)
