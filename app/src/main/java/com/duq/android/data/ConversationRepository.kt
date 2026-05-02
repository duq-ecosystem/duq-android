package com.duq.android.data

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.duq.android.data.local.dao.ConversationDao
import com.duq.android.data.local.dao.MessageDao
import com.duq.android.data.local.entities.ConversationEntity
import com.duq.android.data.local.entities.MessageEntity
import com.duq.android.data.model.Conversation
import com.duq.android.data.model.Message
import com.duq.android.data.model.MessageRole
import com.duq.android.network.ConversationApiClient
import com.duq.android.network.ConversationResponse
import com.duq.android.network.MessageResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val apiClient: ConversationApiClient,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    companion object {
        private const val TAG = "ConversationRepository"
        private const val DEFAULT_MESSAGES_LIMIT = 50
        // Placeholder waveform for voice messages without actual waveform data
        private const val PLACEHOLDER_WAVEFORM = "[0.3,0.5,0.7,0.4,0.6,0.8,0.5,0.3,0.6,0.7,0.5,0.4,0.6,0.8,0.5,0.3]"
    }

    /**
     * Get current active conversation ID or create new one
     */
    suspend fun getCurrentConversationId(authToken: String): String? {
        return try {
            // Try to get from local cache first
            val cachedConv = conversationDao.getActiveConversation()
            if (cachedConv != null) {
                Log.d(TAG, "Using cached conversation: ${cachedConv.id}")
                return cachedConv.id
            }

            // Fetch from API
            val conversations = apiClient.getConversations(authToken).getOrNull()
            if (!conversations.isNullOrEmpty()) {
                // Save to cache
                conversationDao.insertConversations(conversations.map { it.toEntity() })

                // Return most recent active conversation
                val activeConv = conversations.firstOrNull { it.isActive }
                if (activeConv != null) {
                    Log.d(TAG, "Using API conversation: ${activeConv.id}")
                    return activeConv.id
                }
            }

            // Create new conversation if none exist
            val newConv = apiClient.createConversation(authToken, null).getOrNull()
            if (newConv != null) {
                conversationDao.insertConversation(newConv.toEntity())
                Log.d(TAG, "Created new conversation: ${newConv.id}")
                return newConv.id
            }

            Log.e(TAG, "Failed to get or create conversation")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current conversation: ${e.message}")
            null
        }
    }

    /**
     * Get all conversations (from cache, optionally refresh from API)
     */
    suspend fun getConversations(
        authToken: String,
        forceRefresh: Boolean = false
    ): Result<List<Conversation>> {
        return try {
            if (forceRefresh) {
                // Fetch from API and sync cache (add new, remove deleted)
                val apiResult = apiClient.getConversations(authToken)
                if (apiResult.isSuccess) {
                    val conversations = apiResult.getOrNull() ?: emptyList()

                    // Insert/update conversations from API
                    conversationDao.insertConversations(conversations.map { it.toEntity() })

                    // Delete local conversations that no longer exist on server
                    val apiIds = conversations.map { it.id }
                    if (apiIds.isNotEmpty()) {
                        conversationDao.deleteConversationsNotIn(apiIds)
                    }

                    Log.d(TAG, "Synced ${conversations.size} conversations from API")
                }
            }

            // Return from cache
            val cached = conversationDao.getAllConversations()
            Log.d(TAG, "Returning ${cached.size} conversations from cache")
            Result.success(cached.map { it.toDomain() })
        } catch (e: Exception) {
            Log.e(TAG, "Error getting conversations: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get conversations as Flow for reactive UI updates
     */
    fun getConversationsFlow(): Flow<List<Conversation>> {
        return conversationDao.getAllConversationsFlow()
            .map { entities -> entities.map { it.toDomain() } }
    }

    /**
     * Get messages for a conversation (from cache, optionally refresh from API)
     */
    suspend fun getMessages(
        authToken: String,
        conversationId: String,
        forceRefresh: Boolean = false
    ): Result<List<Message>> {
        return try {
            if (forceRefresh) {
                // Fetch from API and update cache
                val apiResult = apiClient.getMessages(authToken, conversationId, DEFAULT_MESSAGES_LIMIT)
                if (apiResult.isSuccess) {
                    val messages = apiResult.getOrNull() ?: emptyList()
                    messageDao.insertMessages(messages.map { it.toEntity() })
                    Log.d(TAG, "Refreshed ${messages.size} messages from API")
                }
            }

            // Return from cache
            val cached = messageDao.getMessagesForConversation(conversationId)
            Log.d(TAG, "Returning ${cached.size} messages from cache")
            Result.success(cached.map { it.toDomain() })
        } catch (e: Exception) {
            Log.e(TAG, "Error getting messages: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get messages as Flow for reactive UI updates
     */
    fun getMessagesFlow(conversationId: String): Flow<List<Message>> {
        Log.d(TAG, "🔄 getMessagesFlow called for conversation: $conversationId")
        return messageDao.getMessagesForConversationFlow(conversationId)
            .map { entities ->
                Log.d(TAG, "📨 Flow emitting ${entities.size} messages for conversation $conversationId")
                entities.map { it.toDomain() }
            }
    }

    /**
     * Refresh messages from API.
     * Uses smart sync: inserts new messages, keeps existing ones.
     * API returns latest N messages; we merge with local to build full history.
     */
    suspend fun refreshMessages(authToken: String, conversationId: String) {
        try {
            val apiResult = apiClient.getMessages(authToken, conversationId, DEFAULT_MESSAGES_LIMIT)
            if (apiResult.isSuccess) {
                val messages = apiResult.getOrNull() ?: emptyList()
                Log.d(TAG, "📥 Got ${messages.size} messages from API")

                val voiceCount = messages.count { it.hasAudio }
                if (voiceCount > 0) {
                    Log.d(TAG, "🎤 Voice messages count: $voiceCount")
                }

                val entities = messages.map { it.toEntity() }

                // Delete temp messages before inserting synced ones
                // Temp messages have IDs like "temp-UUID" and would duplicate with real server IDs
                val deletedTempCount = messageDao.deleteTempMessages(conversationId)
                if (deletedTempCount > 0) {
                    Log.d(TAG, "🗑️ Deleted $deletedTempCount temp messages")
                }

                // UPSERT: OnConflictStrategy.REPLACE will update existing, add new
                // This preserves any messages not returned by API (older than limit)
                messageDao.insertMessages(entities)
                Log.d(TAG, "✅ Synced ${entities.size} messages")
            } else {
                Log.e(TAG, "Failed to refresh messages: ${apiResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing messages: ${e.message}", e)
        }
    }

    /**
     * Create a new conversation
     */
    suspend fun createConversation(
        authToken: String,
        title: String? = null
    ): Result<Conversation> {
        return try {
            val apiResult = apiClient.createConversation(authToken, title)
            val conv = apiResult.getOrNull()
            if (conv != null) {
                conversationDao.insertConversation(conv.toEntity())
                Log.d(TAG, "Created conversation: ${conv.id}")
                Result.success(conv.toDomain())
            } else {
                val errorMsg = apiResult.exceptionOrNull()?.message ?: "Failed to create conversation"
                Log.e(TAG, "Failed to create conversation: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating conversation: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Insert a local message for optimistic UI update.
     * Uses temp- prefix UUID to avoid conflicts with server-generated IDs.
     * The message will be replaced when refreshMessages() fetches the real one.
     *
     * Handles FK constraint violations gracefully - if conversation was deleted
     * between check and insert, logs error and returns false.
     */
    suspend fun insertLocalMessage(
        conversationId: String,
        content: String,
        role: String = "user",
        hasAudio: Boolean = false,
        waveform: List<Float>? = null,
        audioDurationMs: Int? = null
    ): Boolean {
        // Pre-check: verify conversation exists (fast-fail for obvious errors)
        val conversationExists = conversationDao.getConversationById(conversationId) != null
        if (!conversationExists) {
            Log.e(TAG, "❌ CANNOT insert message - conversation $conversationId NOT in local DB!")
            Log.e(TAG, "❌ This is a Foreign Key constraint issue. Conversation must be cached first.")
            return false
        }

        // Use real waveform if provided, otherwise generate placeholder for voice messages
        val waveformJson = when {
            waveform != null && waveform.isNotEmpty() -> {
                com.google.gson.Gson().toJson(waveform)
            }
            hasAudio -> PLACEHOLDER_WAVEFORM
            else -> null
        }

        val tempId = "temp-${java.util.UUID.randomUUID()}" // Temp ID to avoid conflicts
        val entity = MessageEntity(
            id = tempId,
            conversationId = conversationId,
            role = role,
            content = content,
            hasAudio = hasAudio,
            audioDurationMs = audioDurationMs ?: if (hasAudio) 1000 else null,
            waveform = waveformJson,
            createdAt = System.currentTimeMillis() / 1000
        )

        return try {
            messageDao.insertMessage(entity)
            Log.d(TAG, "✅ Inserted local message with temp id: $tempId, hasAudio=$hasAudio, waveform=${waveform?.size ?: 0} points")
            true
        } catch (e: SQLiteConstraintException) {
            // FK constraint violation - conversation was deleted between check and insert
            Log.e(TAG, "❌ FK constraint violation: conversation $conversationId was deleted during insert", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ FAILED to insert local message: ${e.message}", e)
            false
        }
    }

    /**
     * Insert a local message with a specific ID (for WebSocket messages with cached audio).
     * The ID must match the cached audio file ID.
     *
     * Handles FK constraint violations gracefully.
     */
    suspend fun insertLocalMessageWithId(
        messageId: String,
        conversationId: String,
        content: String,
        role: String = "user",
        hasAudio: Boolean = false,
        waveform: List<Float>? = null,
        audioDurationMs: Int? = null
    ): Boolean {
        // Pre-check: verify conversation exists
        val conversationExists = conversationDao.getConversationById(conversationId) != null
        if (!conversationExists) {
            Log.e(TAG, "❌ CANNOT insert message - conversation $conversationId NOT in local DB!")
            return false
        }

        val waveformJson = when {
            waveform != null && waveform.isNotEmpty() -> {
                com.google.gson.Gson().toJson(waveform)
            }
            hasAudio -> PLACEHOLDER_WAVEFORM
            else -> null
        }

        val entity = MessageEntity(
            id = messageId,
            conversationId = conversationId,
            role = role,
            content = content,
            hasAudio = hasAudio,
            audioDurationMs = audioDurationMs ?: if (hasAudio) 1000 else null,
            waveform = waveformJson,
            createdAt = System.currentTimeMillis() / 1000
        )

        return try {
            messageDao.insertMessage(entity)
            Log.d(TAG, "✅ Inserted local message with id: $messageId, hasAudio=$hasAudio")
            true
        } catch (e: SQLiteConstraintException) {
            Log.e(TAG, "❌ FK constraint violation: conversation $conversationId was deleted during insert", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ FAILED to insert local message: ${e.message}", e)
            false
        }
    }

    /**
     * Download and cache audio for a message
     */
    suspend fun downloadAndCacheAudio(
        authToken: String,
        messageId: String
    ): Result<ByteArray> {
        return try {
            apiClient.downloadAudio(authToken, messageId)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading audio: ${e.message}")
            Result.failure(e)
        }
    }

    // Extension functions for conversion
    private fun ConversationResponse.toEntity(): ConversationEntity =
        ConversationEntity(
            id = id,
            userId = userId,
            title = title,
            startedAt = startedAt,
            lastMessageAt = lastMessageAt,
            isActive = isActive
        )

    private fun ConversationResponse.toDomain(): Conversation =
        Conversation(
            id = id,
            userId = userId,
            title = title,
            startedAt = Instant.ofEpochSecond(startedAt),
            lastMessageAt = Instant.ofEpochSecond(lastMessageAt),
            isActive = isActive
        )

    private fun MessageResponse.toEntity(): MessageEntity {
        val waveformString = waveform?.let {
            com.google.gson.Gson().toJson(it)
        }
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            hasAudio = hasAudio,
            audioDurationMs = audioDurationMs,
            waveform = waveformString,
            createdAt = createdAt
        )
    }

    private fun MessageResponse.toDomain(): Message =
        Message(
            id = id,
            conversationId = conversationId,
            role = MessageRole.fromApiString(role),
            content = content,
            hasAudio = hasAudio,
            audioDurationMs = audioDurationMs,
            waveform = waveform,
            createdAt = Instant.ofEpochSecond(createdAt)
        )
}
