package com.jarvis.android.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jarvis.android.data.model.Conversation
import java.time.Instant

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "title")
    val title: String?,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "last_message_at")
    val lastMessageAt: Long,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean
) {
    fun toDomain(): Conversation = Conversation(
        id = id,
        userId = userId,
        title = title,
        startedAt = Instant.ofEpochSecond(startedAt),
        lastMessageAt = Instant.ofEpochSecond(lastMessageAt),
        isActive = isActive
    )

    companion object {
        fun fromDomain(conversation: Conversation): ConversationEntity =
            ConversationEntity(
                id = conversation.id,
                userId = conversation.userId,
                title = conversation.title,
                startedAt = conversation.startedAt.epochSecond,
                lastMessageAt = conversation.lastMessageAt.epochSecond,
                isActive = conversation.isActive
            )
    }
}
