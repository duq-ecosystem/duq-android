package com.duq.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duq.android.data.ConversationRepository
import com.duq.android.data.SettingsRepository
import com.duq.android.data.model.Conversation
import com.duq.android.data.model.Message
import com.duq.android.error.DuqError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ConversationViewModel"
    }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)

    // Messages flow - automatically updates when Room DB changes
    val messages: StateFlow<List<Message>> = _currentConversationId
        .flatMapLatest { conversationId ->
            if (conversationId != null) {
                conversationRepository.getMessagesFlow(conversationId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<DuqError?>(null)
    val error: StateFlow<DuqError?> = _error.asStateFlow()

    init {
        loadConversationsAndMessages()
    }

    /**
     * Load current conversation and its messages
     */
    fun loadConversationsAndMessages() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val authToken = settingsRepository.getAccessToken()
                if (authToken.isEmpty()) {
                    Log.w(TAG, "No auth token available")
                    _isLoading.value = false
                    return@launch
                }

                // Get conversations
                val conversationsResult = conversationRepository.getConversations(authToken, forceRefresh = false)
                if (conversationsResult.isSuccess) {
                    _conversations.value = conversationsResult.getOrNull() ?: emptyList()
                }

                // Get current active conversation ID
                val conversationId = conversationRepository.getCurrentConversationId(authToken)
                if (conversationId != null) {
                    // Fetch initial messages from API before setting conversation ID
                    Log.d(TAG, "Fetching initial messages for conversation $conversationId")
                    conversationRepository.refreshMessages(authToken, conversationId)

                    // Set current conversation ID - this will trigger Flow to load messages from Room
                    _currentConversationId.value = conversationId
                    _currentConversation.value = _conversations.value.find { it.id == conversationId }

                    Log.d(TAG, "Set current conversation $conversationId - Flow active")
                } else {
                    Log.w(TAG, "No current conversation")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading conversations: ${e.message}")
                _error.value = when (e) {
                    is java.net.SocketTimeoutException -> DuqError.NetworkError.timeout()
                    is java.net.UnknownHostException -> DuqError.NetworkError.noConnection()
                    is java.io.IOException -> DuqError.NetworkError(e.message ?: "Network error")
                    else -> DuqError.NetworkError("Failed to load conversations: ${e.message}")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh messages from API (pull-on-focus)
     * Flow will automatically update UI when Room DB changes
     */
    fun refreshMessages() {
        viewModelScope.launch {
            try {
                val authToken = settingsRepository.getAccessToken()
                if (authToken.isEmpty()) return@launch

                // Get current conversation ID
                val conversationId = _currentConversationId.value ?: run {
                    Log.w(TAG, "No current conversation to refresh")
                    return@launch
                }

                Log.d(TAG, "Refreshing messages for conversation $conversationId")
                // Update Room DB - Flow will automatically emit new data to UI
                conversationRepository.refreshMessages(authToken, conversationId)
                Log.d(TAG, "✅ Messages refresh triggered - Flow will update UI automatically")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing messages: ${e.message}")
            }
        }
    }

    /**
     * Load messages for a specific conversation
     * Switch conversation and let Flow handle message loading
     */
    fun loadMessagesForConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val authToken = settingsRepository.getAccessToken()
                if (authToken.isEmpty()) return@launch

                // Fetch fresh data from API
                conversationRepository.refreshMessages(authToken, conversationId)

                // Switch conversation ID - Flow will automatically load messages
                _currentConversationId.value = conversationId
                _currentConversation.value = _conversations.value.find { it.id == conversationId }

                Log.d(TAG, "Switched to conversation $conversationId - Flow will load messages")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages: ${e.message}")
                _error.value = when (e) {
                    is java.net.SocketTimeoutException -> DuqError.NetworkError.timeout()
                    is java.net.UnknownHostException -> DuqError.NetworkError.noConnection()
                    is java.io.IOException -> DuqError.NetworkError(e.message ?: "Network error")
                    else -> DuqError.NetworkError("Failed to load messages: ${e.message}")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Create a new conversation
     */
    fun createConversation(title: String? = null) {
        viewModelScope.launch {
            try {
                val authToken = settingsRepository.getAccessToken()
                if (authToken.isEmpty()) return@launch

                val result = conversationRepository.createConversation(authToken, title)
                result.getOrNull()?.let { newConv ->
                    _conversations.value = _conversations.value + newConv

                    // Switch to new conversation - Flow will load (empty) messages
                    _currentConversationId.value = newConv.id
                    _currentConversation.value = newConv

                    Log.d(TAG, "Created new conversation: ${newConv.id}")
                } ?: run {
                    Log.e(TAG, "Failed to create conversation: result was null")
                    _error.value = DuqError.NetworkError("Failed to create conversation")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating conversation: ${e.message}")
                _error.value = when (e) {
                    is java.net.SocketTimeoutException -> DuqError.NetworkError.timeout()
                    is java.net.UnknownHostException -> DuqError.NetworkError.noConnection()
                    is java.io.IOException -> DuqError.NetworkError(e.message ?: "Network error")
                    else -> DuqError.NetworkError("Failed to create conversation: ${e.message}")
                }
            }
        }
    }

    /**
     * Clear error
     */
    fun clearError() {
        _error.value = null
    }
}
