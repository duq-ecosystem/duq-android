package com.duq.android.network

import android.util.Log
import com.duq.android.BuildConfig
import com.duq.android.data.SettingsRepository
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.duq.android.auth.KeycloakConfig
import com.duq.android.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket client for real-time communication with Duq Gateway.
 *
 * Handles:
 * - Connection management with auto-reconnect
 * - Response streaming from DUQ agent
 * - Task correlation by task_id
 */
@Singleton
class DuqWebSocketClient @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "DuqWebSocketClient"

        // WebSocket URL (same domain as API, /ws endpoint)
        val WS_URL: String = BuildConfig.API_BASE_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/ws"

        // Reconnection settings
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val RECONNECT_MULTIPLIER = 2.0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private var isManuallyDisconnected = false

    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Incoming messages
    private val _messages = MutableSharedFlow<WSMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<WSMessage> = _messages

    private val client by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)  // No timeout for WebSocket
            .pingInterval(30, TimeUnit.SECONDS)     // Keep-alive pings
            .build()
    }

    /**
     * Connection states
     */
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()

        override fun toString(): String = when (this) {
            is Disconnected -> "Disconnected"
            is Connecting -> "Connecting"
            is Connected -> "Connected"
            is Error -> "Error: $message"
        }
    }

    /**
     * WebSocket message from server
     */
    data class WSMessage(
        @SerializedName("type") val type: String,
        @SerializedName("task_id") val taskId: String? = null,
        @SerializedName("text") val text: String? = null,
        @SerializedName("voice_data") val voiceData: String? = null,  // Base64 encoded
        @SerializedName("error") val error: String? = null,
        @SerializedName("timestamp") val timestamp: Long? = null
    )

    /**
     * Connect to WebSocket server.
     * Uses Keycloak JWT token for authentication.
     * Automatically refreshes token if expired.
     */
    suspend fun connect() {
        if (_connectionState.value == ConnectionState.Connected ||
            _connectionState.value == ConnectionState.Connecting) {
            Log.d(TAG, "Already connected or connecting, skipping")
            return
        }

        isManuallyDisconnected = false
        _connectionState.value = ConnectionState.Connecting

        try {
            // Check if token needs refresh
            if (settingsRepository.isTokenExpired()) {
                Log.d(TAG, "Token expired, attempting refresh...")
                if (!refreshToken()) {
                    Log.e(TAG, "Token refresh failed")
                    _connectionState.value = ConnectionState.Error("Token expired, please re-login")
                    return
                }
            }

            val token = settingsRepository.accessToken.first()
            val deviceId = settingsRepository.deviceId.first()

            if (token.isEmpty()) {
                Log.e(TAG, "No access token available")
                _connectionState.value = ConnectionState.Error("No access token")
                return
            }

            val wsUrl = "$WS_URL?token=$token&device_id=$deviceId"
            Log.d(TAG, "Connecting to WebSocket: $WS_URL")

            val request = Request.Builder()
                .url(wsUrl)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected")
                    reconnectAttempt = 0
                    _connectionState.value = ConnectionState.Connected
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "WebSocket message: ${text.take(200)}${if (text.length > 200) "..." else ""}")

                    try {
                        val message = gson.fromJson(text, WSMessage::class.java)

                        // Handle ping/pong internally
                        if (message.type == "pong") {
                            Log.d(TAG, "Received pong")
                            return
                        }

                        // Emit message to listeners
                        scope.launch {
                            _messages.emit(message)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse WebSocket message", e)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code $reason")
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code $reason")
                    _connectionState.value = ConnectionState.Disconnected

                    // Auto-reconnect if not manually disconnected
                    if (!isManuallyDisconnected && code != 1000) {
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}", t)
                    _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")

                    // Auto-reconnect
                    if (!isManuallyDisconnected) {
                        scheduleReconnect()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect WebSocket", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
        }
    }

    /**
     * Disconnect from WebSocket server.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        isManuallyDisconnected = true
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Send a ping to keep connection alive
     */
    fun sendPing() {
        val ping = """{"type":"ping"}"""
        webSocket?.send(ping)
    }

    /**
     * Schedule reconnection with exponential backoff
     */
    private fun scheduleReconnect() {
        scope.launch {
            val delay = calculateReconnectDelay()
            Log.d(TAG, "Scheduling reconnect in ${delay}ms (attempt $reconnectAttempt)")

            delay(delay)
            reconnectAttempt++

            if (!isManuallyDisconnected) {
                connect()
            }
        }
    }

    private fun calculateReconnectDelay(): Long {
        val delay = INITIAL_RECONNECT_DELAY_MS * Math.pow(RECONNECT_MULTIPLIER, reconnectAttempt.toDouble())
        return delay.toLong().coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    /**
     * Check if connected
     */
    fun isConnected(): Boolean = _connectionState.value == ConnectionState.Connected

    /**
     * Refresh access token using Keycloak refresh token.
     * Returns true if refresh was successful.
     */
    private suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val refreshToken = settingsRepository.getRefreshToken()
            if (refreshToken.isBlank()) {
                Log.w(TAG, "No refresh token available")
                return@withContext false
            }

            val formBody = okhttp3.FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", KeycloakConfig.CLIENT_ID)
                .add("refresh_token", refreshToken)
                .build()

            val refreshClient = OkHttpClient.Builder()
                .connectTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(AppConfig.AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder()
                .url(KeycloakConfig.TOKEN_ENDPOINT.toString())
                .post(formBody)
                .build()

            val response = refreshClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)

                val newAccessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", null)
                val expiresIn = json.optInt("expires_in", AppConfig.DEFAULT_TOKEN_EXPIRES_S)
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                settingsRepository.updateAccessToken(
                    accessToken = newAccessToken,
                    refreshToken = newRefreshToken,
                    expiresAt = expiresAt
                )

                Log.d(TAG, "Token refreshed successfully")
                true
            } else {
                Log.e(TAG, "Token refresh failed: HTTP ${response.code}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh exception", e)
            false
        }
    }
}
