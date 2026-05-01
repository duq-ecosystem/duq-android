package com.duq.android.ui

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duq.android.audio.ChatAudioPlaybackManager
import com.duq.android.config.AppConfig
import com.duq.android.data.ConversationRepository
import com.duq.android.data.SettingsRepository
import com.duq.android.data.model.Conversation
import com.duq.android.data.model.Message
import com.duq.android.error.DuqError
import com.duq.android.network.DuqApiClient
import com.duq.android.network.DuqWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val duqApiClient: DuqApiClient,
    private val webSocketClient: DuqWebSocketClient,
    private val audioPlaybackManager: ChatAudioPlaybackManager
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

    private val _error = MutableStateFlow<DuqError?>(null)
    val error: StateFlow<DuqError?> = _error.asStateFlow()

    // Processing state for text messages (syncs with duck animation)
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    init {
        loadConversationsAndMessages()
        startWebSocketListener()
    }

    /**
     * Start background WebSocket listener for real-time sync.
     * Processes ALL incoming messages (from Telegram, MCP, etc.)
     * and updates the conversation UI.
     */
    private fun startWebSocketListener() {
        viewModelScope.launch {
            webSocketClient.messages.collect { message ->
                Log.d(TAG, "🔔 WebSocket message received: type=${message.type}, taskId=${message.taskId}")

                // Only process response/notification messages
                if (message.type != "response" && message.type != "notification") {
                    return@collect
                }

                // Get current conversation ID
                val conversationId = _currentConversationId.value ?: return@collect

                // Insert assistant message to local Room DB
                // Flow will automatically update UI
                val text = message.text ?: return@collect
                if (text.isNotBlank()) {
                    val hasVoice = !message.voiceData.isNullOrEmpty()
                    Log.d(TAG, "📥 Adding WebSocket message: ${text.take(50)}..., hasVoice=$hasVoice, waveform=${message.waveform?.size ?: 0} points")

                    // Generate temp message ID for this message
                    val tempMessageId = "temp-${java.util.UUID.randomUUID()}"

                    // Save audio to cache if present (for offline playback)
                    if (hasVoice && message.voiceData != null) {
                        try {
                            val audioBytes = Base64.decode(message.voiceData, Base64.DEFAULT)
                            audioPlaybackManager.saveToCache(tempMessageId, audioBytes)
                            Log.d(TAG, "🎵 Cached WebSocket audio: ${audioBytes.size} bytes")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to cache audio: ${e.message}")
                        }
                    }

                    conversationRepository.insertLocalMessageWithId(
                        messageId = tempMessageId,
                        conversationId = conversationId,
                        content = text,
                        role = "assistant",
                        hasAudio = hasVoice,
                        waveform = message.waveform,
                        audioDurationMs = message.audioDurationMs
                    )

                    Log.d(TAG, "✅ Real-time sync: assistant message added to UI")
                }
            }
        }
    }

    /**
     * Load current conversation and its messages
     */
    fun loadConversationsAndMessages() {
        viewModelScope.launch {
            try {
                _error.value = null

                val authToken = settingsRepository.getAccessToken()
                if (authToken.isEmpty()) {
                    Log.w(TAG, "No auth token available")
                    return@launch
                }

                // Get conversations - force refresh to sync with Telegram history
                val conversationsResult = conversationRepository.getConversations(authToken, forceRefresh = true)
                if (conversationsResult.isSuccess) {
                    _conversations.value = conversationsResult.getOrNull() ?: emptyList()
                    Log.d(TAG, "Synced ${_conversations.value.size} conversations from server")
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
     * Send a text message to Duq via WebSocket (no polling!)
     * Uses optimistic update - message appears immediately, no loading spinner.
     */
    fun sendTextMessage(message: String) {
        Log.d(TAG, "🔘 sendTextMessage called with: '${message.take(50)}'")
        if (message.isBlank()) {
            Log.w(TAG, "⚠️ Message is blank, ignoring")
            return
        }

        viewModelScope.launch {
            try {
                // NO loading indicator - optimistic update shows message immediately
                _error.value = null

                val authToken = settingsRepository.getAccessToken()
                if (authToken.isEmpty()) {
                    Log.w(TAG, "No auth token available")
                    _error.value = DuqError.AuthError("Not authenticated")
                    return@launch
                }

                val userId = settingsRepository.userSub.first()
                if (userId.isEmpty()) {
                    Log.w(TAG, "No user ID available")
                    _error.value = DuqError.AuthError("No user ID")
                    return@launch
                }

                Log.d(TAG, "📤 Sending text message: ${message.take(50)}...")

                // Optimistic update: show user message immediately
                val conversationId = _currentConversationId.value
                if (conversationId != null) {
                    conversationRepository.insertLocalMessage(conversationId, message, "user")
                    Log.d(TAG, "✅ Optimistic update: user message added to UI")
                }

                // Ensure WebSocket is connected
                if (!webSocketClient.isConnected()) {
                    Log.d(TAG, "🔌 Connecting WebSocket...")
                    webSocketClient.connect()
                }

                // Start processing state (duck animation)
                _isProcessing.value = true

                when (val result = duqApiClient.queueTextMessage(authToken, message, userId)) {
                    is DuqApiClient.SendResult.Queued -> {
                        Log.d(TAG, "📨 Message queued, task_id: ${result.taskId}")

                        // Wait for WebSocket response (NO POLLING!)
                        val wsResponse = withTimeoutOrNull(AppConfig.WS_RESPONSE_TIMEOUT_MS) {
                            webSocketClient.messages
                                .filter { it.taskId == result.taskId }
                                .first()
                        }

                        if (wsResponse != null) {
                            Log.d(TAG, "✅ WebSocket response: type=${wsResponse.type}")
                            when (wsResponse.type) {
                                "response", "success" -> {
                                    Log.d(TAG, "✅ Task completed via WebSocket")
                                }
                                "error" -> {
                                    Log.e(TAG, "❌ Task failed: ${wsResponse.error}")
                                    _error.value = DuqError.NetworkError(wsResponse.error ?: "Task failed")
                                }
                            }
                        } else {
                            Log.w(TAG, "⏱️ WebSocket timeout, refreshing messages anyway")
                        }

                        // Refresh messages from server
                        refreshMessages()
                    }
                    is DuqApiClient.SendResult.Error -> {
                        Log.e(TAG, "❌ Failed to queue message: ${result.message}")
                        _error.value = DuqError.NetworkError(result.message)
                    }
                    else -> {
                        Log.w(TAG, "Unexpected result: $result")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message: ${e.message}")
                _error.value = when (e) {
                    is java.net.SocketTimeoutException -> DuqError.NetworkError.timeout()
                    is java.net.UnknownHostException -> DuqError.NetworkError.noConnection()
                    is java.io.IOException -> DuqError.NetworkError(e.message ?: "Network error")
                    else -> DuqError.NetworkError("Failed to send message: ${e.message}")
                }
            } finally {
                _isProcessing.value = false
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
