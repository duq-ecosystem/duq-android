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
) : VoiceApiClientInterface {

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
        audioFile: File
    ): ApiResult = withContext(Dispatchers.IO) {
        try {
            withRetry {
                sendVoiceCommandInternal(authToken, audioFile)
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

    private fun sendVoiceCommandInternal(authToken: String, audioFile: File): ApiResult {
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🎤 VOICE COMMAND START")
        Log.d(TAG, "Audio file: ${audioFile.name}")
        Log.d(TAG, "File size: ${audioFile.length()} bytes (${audioFile.length() / 1024}KB)")
        Log.d(TAG, "URL: $BASE_URL/api/voice")
        // Security: Don't log auth tokens

        val mediaType = "audio/wav".toMediaType()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                audioFile.name,
                audioFile.asRequestBody(mediaType)
            )
            .build()

        val url = "$BASE_URL/api/voice"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .post(requestBody)
            .build()

        Log.d(TAG, "📤 Uploading audio...")
        val startTime = System.currentTimeMillis()

        val response = client.newCall(request).execute()
        val duration = System.currentTimeMillis() - startTime

        Log.d(TAG, "📥 Response received in ${duration}ms")
        Log.d(TAG, "Status: ${response.code} ${response.message}")

        if (response.isSuccessful) {
            val body = response.body?.string()
            if (body != null && body.isNotEmpty()) {
                Log.d(TAG, "Response body length: ${body.length} chars")

                val json = JSONObject(body)
                val text = json.optString("text", "")
                val audioBase64 = json.optString("audio", "")

                Log.d(TAG, "Response text: \"${text.take(100)}${if (text.length > 100) "..." else ""}\"")
                Log.d(TAG, "Audio base64 length: ${audioBase64.length} chars")

                if (audioBase64.isNotEmpty()) {
                    val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                    Log.d(TAG, "✅ VOICE COMMAND SUCCESS")
                    Log.d(TAG, "Decoded audio: ${audioBytes.size} bytes (${audioBytes.size / 1024}KB)")
                    Log.d(TAG, "Total time: ${duration}ms")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    return ApiResult.Success(audioBytes, text)
                } else {
                    Log.e(TAG, "❌ No audio in response")
                    Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    return ApiResult.Error("No audio in response")
                }
            } else {
                Log.e(TAG, "❌ Empty response from server")
                Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                return ApiResult.Error("Empty response from server")
            }
        } else {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "❌ VOICE COMMAND FAILED")
            Log.e(TAG, "HTTP ${response.code}: ${response.message}")
            Log.e(TAG, "Error: $errorBody")
            Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            return ApiResult.Error(errorBody, response.code)
        }
    }

    /**
     * Get list of user's conversations with retry on transient failures
     */
    suspend fun getConversations(authToken: String): Result<List<ConversationResponse>> =
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
    suspend fun getMessages(
        authToken: String,
        conversationId: String,
        limit: Int = AppConfig.DEFAULT_MESSAGES_LIMIT
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
    suspend fun createConversation(
        authToken: String,
        title: String? = null
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
    suspend fun downloadAudio(
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
