package com.duq.android.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.duq.android.data.model.Message
import com.duq.android.data.model.MessageRole
import java.time.Instant

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversation_id"])]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "has_audio")
    val hasAudio: Boolean,

    @ColumnInfo(name = "audio_duration_ms")
    val audioDurationMs: Int?,

    @ColumnInfo(name = "waveform")
    val waveform: String?, // JSON string of List<Float>

    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    fun toDomain(): Message {
        val waveformList = waveform?.let {
            try {
                com.google.gson.Gson().fromJson(it, Array<Float>::class.java).toList()
            } catch (e: Exception) {
                null
            }
        }
        return Message(
            id = id,
            conversationId = conversationId,
            role = MessageRole.fromApiString(role),
            content = content,
            hasAudio = hasAudio,
            audioDurationMs = audioDurationMs,
            waveform = waveformList,
            createdAt = Instant.ofEpochSecond(createdAt)
        )
    }

    companion object {
        fun fromDomain(message: Message): MessageEntity {
            val waveformString = message.waveform?.let {
                com.google.gson.Gson().toJson(it)
            }
            return MessageEntity(
                id = message.id,
                conversationId = message.conversationId,
                role = message.role.toApiString(),
                content = message.content,
                hasAudio = message.hasAudio,
                audioDurationMs = message.audioDurationMs,
                waveform = waveformString,
                createdAt = message.createdAt.epochSecond
            )
        }
    }
}
