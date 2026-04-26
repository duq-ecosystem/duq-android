package com.duq.android.network

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.duq.android.BuildConfig
import com.duq.android.auth.KeycloakConfig
import com.duq.android.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

class DuqApiClient(
    private val tokenRefreshInterceptor: TokenRefreshInterceptor? = null
) : VoiceApiClientInterface, ConversationApiClient {

    companion object {
        private const val TAG = "DuqApiClient"

        // API Base URL from BuildConfig (configurable per build variant)
        val BASE_URL: String = BuildConfig.API_BASE_URL
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AppConfig.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(AppConfig.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .apply {
                tokenRefreshInterceptor?.let { addInterceptor(it) }
            }
            .build()
    }

    private val gson = Gson()

    /**
     * Execute a block with exponential backoff retry logic.
     * Retries on transient network errors (timeout, connection issues).
     *
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelayMs Initial delay before first retry
     * @param maxDelayMs Maximum delay between retries
     * @param backoffMultiplier Multiplier for exponential backoff
     * @param isRetryable Lambda to determine if exception should trigger retry
     * @param block The suspend block to execute
     */
    private suspend fun <T> withRetry(
        maxRetries: Int = AppConfig.MAX_RETRIES,
        initialDelayMs: Long = AppConfig.INITIAL_RETRY_DELAY_MS,
        maxDelayMs: Long = AppConfig.MAX_RETRY_DELAY_MS,
        backoffMultiplier: Double = AppConfig.RETRY_MULTIPLIER,
        isRetryable: (Exception) -> Boolean = ::isRetryableException,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (!isRetryable(e) || attempt == maxRetries - 1) {
                    throw e
                }

                Log.w(TAG, "Attempt ${attempt + 1}/$maxRetries failed: ${e.message}, retrying in ${currentDelay}ms")
                delay(currentDelay)
                currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
            }
        }

        throw lastException ?: IOException("Retry failed without exception")
    }

    /**
     * Determine if an exception is retryable (transient network errors).
     */
    private fun isRetryableException(e: Exception): Boolean {
        return when (e) {
            is SocketTimeoutException -> true
            is UnknownHostException -> true
            is java.net.ConnectException -> true
            is javax.net.ssl.SSLException -> e.message?.contains("Connection reset") == true
            is IOException -> e.message?.let {
                it.contains("timeout", ignoreCase = true) ||
                it.contains("connection", ignoreCase = true) ||
                it.contains("reset", ignoreCase = true)
            } ?: false
            else -> false
        }
    }

    sealed class ApiResult {
        data class Success(val audioData: ByteArray, val text: String = "") : ApiResult() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as Success
                return audioData.contentEquals(other.audioData) && text == other.text
            }

            override fun hashCode(): Int = audioData.contentHashCode() + text.hashCode()
        }
        data class Error(val message: String, val code: Int? = null) : ApiResult()
    }

    /**
     * Result of sending voice command via queue (WebSocket mode)
     */
    sealed class SendResult {
        data class Queued(val taskId: String) : SendResult()
        data class Error(val message: String, val code: Int? = null) : SendResult()
    }

    /**
     * Result of token refresh operation
     */
    data class TokenRefreshResult(
        val success: Boolean,
        val accessToken: String = "",
        val refreshToken: String? = null,
        val expiresAt: Long = 0L,
        val error: String = ""
    )

    /**
     * Refresh access token using refresh token via Keycloak
     */
    suspend fun refreshToken(refreshToken: String): TokenRefreshResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Refreshing access token via Keycloak")

            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", KeycloakConfig.CLIENT_ID)
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(KeycloakConfig.TOKEN_ENDPOINT.toString())
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)

                val newAccessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", null)
                val expiresIn = json.optInt("expires_in", AppConfig.DEFAULT_TOKEN_EXPIRES_S)
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                Log.d(TAG, "Token refresh successful, expires in ${expiresIn}s")

                TokenRefreshResult(
                    success = true,
                    accessToken = newAccessToken,
                    refreshToken = newRefreshToken,
                    expiresAt = expiresAt
                )
            } else {
                val errorBody = response.body?.string() ?: "Token refresh failed"
                Log.e(TAG, "Token refresh failed: HTTP ${response.code}")
                TokenRefreshResult(success = false, error = errorBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh exception", e)
            TokenRefreshResult(success = false, error = e.message ?: "Token refresh failed")
        }
    }

    override suspend fun sendVoiceCommand(
        serverUrl: String,
        authToken: String,
        audioFile: File,
        userId: String
    ): ApiResult = withContext(Dispatchers.IO) {
        try {
            withRetry {
                sendVoiceCommandViaQueue(authToken, audioFile, userId)
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ NETWORK ERROR after retries")
            Log.e(TAG, "Error: ${e.javaClass.simpleName}: ${e.message}")
            ApiResult.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ VOICE COMMAND EXCEPTION")
            Log.e(TAG, "Error: ${e.javaClass.simpleName}: ${e.message}")
            ApiResult.Error("Error: ${e.message}")
        }
    }

    /**
     * Send voice command via queue-based API:
     * 1. POST /api/message with base64 audio
     * 2. Poll GET /api/task/{id} for result
     */
    private suspend fun sendVoiceCommandViaQueue(
        authToken: String,
        audioFile: File,
        userId: String
    ): ApiResult {
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🎤 VOICE COMMAND START (Queue API)")
        Log.d(TAG, "Audio file: ${audioFile.name}")
        Log.d(TAG, "File size: ${audioFile.length()} bytes (${audioFile.length() / 1024}KB)")
        Log.d(TAG, "User ID: $userId")
        Log.d(TAG, "URL: $BASE_URL/api/message")

        val startTime = System.currentTimeMillis()

        // Step 1: Convert audio to base64
        val audioBytes = audioFile.readBytes()
        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        Log.d(TAG, "Audio base64 length: ${audioBase64.length} chars")

        // Step 2: Send POST /api/message
        val messageRequest = MessageApiRequest(
            userId = userId,
            message = "[Voice message]",  // Placeholder, Duq will transcribe
            isVoice = true,
            voiceData = audioBase64,
            voiceFormat = "wav",
            source = "android"
        )

        val requestJson = gson.toJson(messageRequest)
        val requestBody = requestJson.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/api/message")
            .addHeader("Authorization", "Bearer $authToken")
            .post(requestBody)
            .build()

        Log.d(TAG, "📤 Sending to queue...")
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "❌ Queue request failed: HTTP ${response.code}")
            Log.e(TAG, "Error: $errorBody")
            return ApiResult.Error(errorBody, response.code)
        }

        val responseBody = response.body?.string() ?: ""
        val messageResponse = gson.fromJson(responseBody, MessageApiResponse::class.java)

        if (messageResponse.error != null) {
            Log.e(TAG, "❌ Queue error: ${messageResponse.error}")
            return ApiResult.Error(messageResponse.error)
        }

        val taskId = messageResponse.taskId ?: return ApiResult.Error("No task_id in response")
        Log.d(TAG, "📋 Task queued: $taskId")

        // Step 3: Poll for result
        val result = pollForTaskResult(authToken, taskId)
        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Total time: ${totalTime}ms")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        return result
    }

    /**
     * Queue voice command via POST /api/message (WebSocket mode).
     * Returns task_id immediately - response will come via WebSocket.
     */
    suspend fun queueVoiceCommand(
        authToken: String,
        audioFile: File,
        userId: String
    ): SendResult = withContext(Dispatchers.IO) {
        try {
            withRetry {
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "🎤 QUEUE VOICE COMMAND (WebSocket mode)")
                Log.d(TAG, "Audio file: ${audioFile.name}")
                Log.d(TAG, "File size: ${audioFile.length()} bytes")
                Log.d(TAG, "User ID: $userId")

                val audioBytes = audioFile.readBytes()
                val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

                val messageRequest = MessageApiRequest(
                    userId = userId,
                    message = "[Voice message]",
                    isVoice = true,
                    voiceData = audioBase64,
                    voiceFormat = "wav",
                    source = "android"
                )

                val requestJson = gson.toJson(messageRequest)
                val requestBody = requestJson.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$BASE_URL/api/message")
                    .addHeader("Authorization", "Bearer $authToken")
                    .post(requestBody)
                    .build()

                Log.d(TAG, "📤 Sending to queue...")
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "❌ Queue request failed: HTTP ${response.code}")
                    return@withRetry SendResult.Error(errorBody, response.code)
                }

                val responseBody = response.body?.string() ?: ""
                val messageResponse = gson.fromJson(responseBody, MessageApiResponse::class.java)

                if (messageResponse.error != null) {
                    Log.e(TAG, "❌ Queue error: ${messageResponse.error}")
                    return@withRetry SendResult.Error(messageResponse.error)
                }

                val taskId = messageResponse.taskId
                    ?: return@withRetry SendResult.Error("No task_id in response")

                Log.d(TAG, "📋 Task queued: $taskId")
                Log.d(TAG, "Waiting for WebSocket response...")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                SendResult.Queued(taskId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Queue voice command exception: ${e.message}")
            SendResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Queue text message via POST /api/message (WebSocket mode).
     * Returns task_id immediately - response will come via WebSocket.
     */
    suspend fun queueTextMessage(
        authToken: String,
        message: String,
        userId: String
    ): SendResult = withContext(Dispatchers.IO) {
        try {
            withRetry {
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "💬 QUEUE TEXT MESSAGE")
                Log.d(TAG, "Message: ${message.take(50)}...")
                Log.d(TAG, "User ID: $userId")

                val messageRequest = MessageApiRequest(
                    userId = userId,
                    message = message,
                    isVoice = false,
                    source = "android"
                )

                val requestJson = gson.toJson(messageRequest)
                val requestBody = requestJson.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$BASE_URL/api/message")
                    .addHeader("Authorization", "Bearer $authToken")
                    .post(requestBody)
                    .build()

                Log.d(TAG, "📤 Sending text message to queue...")
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "❌ Queue request failed: HTTP ${response.code}")
                    return@withRetry SendResult.Error(errorBody, response.code)
                }

                val responseBody = response.body?.string() ?: ""
                val messageResponse = gson.fromJson(responseBody, MessageApiResponse::class.java)

                if (messageResponse.error != null) {
                    Log.e(TAG, "❌ Queue error: ${messageResponse.error}")
                    return@withRetry SendResult.Error(messageResponse.error)
                }

                val taskId = messageResponse.taskId
                    ?: return@withRetry SendResult.Error("No task_id in response")

                Log.d(TAG, "📋 Text message queued: $taskId")
                Log.d(TAG, "Waiting for WebSocket response...")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                SendResult.Queued(taskId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Queue text message exception: ${e.message}")
            SendResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Poll for a specific task result (public method for WebSocket fallback).
     * Used when WebSocket times out but task was already queued.
     */
    suspend fun pollForTask(
        authToken: String,
        taskId: String
    ): ApiResult = withContext(Dispatchers.IO) {
        try {
            withRetry {
                Log.d(TAG, "Polling for task: $taskId")
                pollForTaskResult(authToken, taskId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Poll for task exception: ${e.message}")
            ApiResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Poll GET /api/task/{id} until result is ready
     */
    private suspend fun pollForTaskResult(
        authToken: String,
        taskId: String,
        maxAttempts: Int = 60,  // 60 * 1s = 60s timeout
        pollIntervalMs: Long = 1000
    ): ApiResult {
        Log.d(TAG, "⏳ Polling for task result: $taskId")

        repeat(maxAttempts) { attempt ->
            val request = Request.Builder()
                .url("$BASE_URL/api/task/$taskId")
                .addHeader("Authorization", "Bearer $authToken")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Poll attempt ${attempt + 1}: HTTP ${response.code}")
                delay(pollIntervalMs)
                return@repeat
            }

            val body = response.body?.string() ?: ""
            val taskStatus = gson.fromJson(body, TaskStatusResponse::class.java)

            Log.d(TAG, "Poll attempt ${attempt + 1}: status=${taskStatus.status}")

            when (taskStatus.status.uppercase()) {
                "COMPLETED", "SUCCESS" -> {
                    val taskResponse = taskStatus.response
                    if (taskResponse != null) {
                        val text = taskResponse.text ?: ""
                        val audioBase64 = taskResponse.audio

                        if (audioBase64 != null && audioBase64.isNotEmpty()) {
                            val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                            Log.d(TAG, "✅ VOICE COMMAND SUCCESS")
                            Log.d(TAG, "Response text: \"${text.take(100)}${if (text.length > 100) "..." else ""}\"")
                            Log.d(TAG, "Audio: ${audioBytes.size} bytes")
                            return ApiResult.Success(audioBytes, text)
                        } else {
                            Log.d(TAG, "✅ Text-only response (no audio)")
                            return ApiResult.Success(ByteArray(0), text)
                        }
                    } else {
                        Log.e(TAG, "❌ No response data in completed task")
                        return ApiResult.Error("No response data")
                    }
                }
                "FAILED", "ERROR" -> {
                    val error = taskStatus.error ?: "Task failed"
                    Log.e(TAG, "❌ Task failed: $error")
                    return ApiResult.Error(error)
                }
                "PENDING", "PROCESSING", "QUEUED" -> {
                    // Still processing, continue polling
                    delay(pollIntervalMs)
                }
                else -> {
                    Log.w(TAG, "Unknown status: ${taskStatus.status}")
                    delay(pollIntervalMs)
                }
            }
        }

        Log.e(TAG, "❌ Polling timeout after $maxAttempts attempts")
        return ApiResult.Error("Request timeout")
    }

    /**
     * Get list of user's conversations with retry on transient failures
     */
    override suspend fun getConversations(authToken: String): Result<List<ConversationResponse>> =
        withContext(Dispatchers.IO) {
            try {
                withRetry {
                    Log.d(TAG, "📋 Getting conversations list")

                    val request = Request.Builder()
                        .url("$BASE_URL/api/conversations")
                        .addHeader("Authorization", "Bearer $authToken")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val listType = object : TypeToken<List<ConversationResponse>>() {}.type
                        val conversations = gson.fromJson<List<ConversationResponse>>(body, listType)
                        Log.d(TAG, "✅ Got ${conversations.size} conversations")
                        Result.success(conversations)
                    } else {
                        val error = response.body?.string() ?: "Failed to get conversations"
                        Log.e(TAG, "❌ Get conversations failed: HTTP ${response.code}")
                        Result.failure(IOException(error))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Get conversations exception after retries: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Get messages for a conversation with retry on transient failures
     */
    override suspend fun getMessages(
        authToken: String,
        conversationId: String,
        limit: Int
    ): Result<List<MessageResponse>> = withContext(Dispatchers.IO) {
        try {
            withRetry {
                Log.d(TAG, "💬 Getting messages for conversation $conversationId")

                val request = Request.Builder()
                    .url("$BASE_URL/api/conversations/$conversationId/messages?limit=$limit")
                    .addHeader("Authorization", "Bearer $authToken")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val listType = object : TypeToken<List<MessageResponse>>() {}.type
                    val messages = gson.fromJson<List<MessageResponse>>(body, listType)
                    Log.d(TAG, "✅ Got ${messages.size} messages")
                    Result.success(messages)
                } else {
                    val error = response.body?.string() ?: "Failed to get messages"
                    Log.e(TAG, "❌ Get messages failed: HTTP ${response.code}")
                    Result.failure(IOException(error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Get messages exception after retries: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Create a new conversation with retry on transient failures
     */
    override suspend fun createConversation(
        authToken: String,
        title: String?
    ): Result<ConversationResponse> = withContext(Dispatchers.IO) {
        try {
            withRetry {
                Log.d(TAG, "➕ Creating new conversation")

                val requestJson = gson.toJson(CreateConversationRequest(title))
                val requestBody = requestJson.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$BASE_URL/api/conversations")
                    .addHeader("Authorization", "Bearer $authToken")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val conversation = gson.fromJson(body, ConversationResponse::class.java)
                    Log.d(TAG, "✅ Created conversation: ${conversation.id}")
                    Result.success(conversation)
                } else {
                    val error = response.body?.string() ?: "Failed to create conversation"
                    Log.e(TAG, "❌ Create conversation failed: HTTP ${response.code}")
                    Result.failure(IOException(error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Create conversation exception after retries: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Download audio for a message with retry on transient failures
     */
    override suspend fun downloadAudio(
        authToken: String,
        messageId: Long
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            withRetry {
                Log.d(TAG, "🔊 Downloading audio for message $messageId")

                val request = Request.Builder()
                    .url("$BASE_URL/api/messages/$messageId/audio")
                    .addHeader("Authorization", "Bearer $authToken")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val audioBytes = response.body?.bytes() ?: ByteArray(0)
                    Log.d(TAG, "✅ Downloaded ${audioBytes.size} bytes")
                    Result.success(audioBytes)
                } else {
                    val error = response.body?.string() ?: "Failed to download audio"
                    Log.e(TAG, "❌ Download audio failed: HTTP ${response.code}")
                    Result.failure(IOException(error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Download audio exception after retries: ${e.message}")
            Result.failure(e)
        }
    }
}
