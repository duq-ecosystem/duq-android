package com.jarvis.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.android.data.local.entities.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY last_message_at DESC")
    fun getAllConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY last_message_at DESC")
    suspend fun getAllConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE is_active = 1 ORDER BY last_message_at DESC LIMIT 1")
    suspend fun getActiveConversation(): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)
}
