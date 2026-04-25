package com.duq.android.service

import android.util.Log
import com.duq.android.data.ConversationRepository
import javax.inject.Inject

/**
 * Handles conversation history updates from backend.
 * Extracted from VoiceCommandProcessor for SRP.
 */
interface ConversationUpdater {
    suspend fun refreshConversation(authToken: String)
}

class DefaultConversationUpdater @Inject constructor(
    private val conversationRepository: ConversationRepository
) : ConversationUpdater {

    companion object {
        private const val TAG = "ConversationUpdater"
    }

    override suspend fun refreshConversation(authToken: String) {
        try {
            Log.d(TAG, "Refreshing conversation history from backend...")
            val conversationId = conversationRepository.getCurrentConversationId(authToken)
            if (conversationId != null) {
                conversationRepository.refreshMessages(authToken, conversationId)
                Log.d(TAG, "Conversation history refreshed")
            } else {
                Log.w(TAG, "No active conversation to refresh")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh conversation: ${e.message}")
        }
    }
}
