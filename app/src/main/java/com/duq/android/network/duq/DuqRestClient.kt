package com.duq.android.network.duq

import com.duq.android.config.AppConfig
import com.duq.android.logging.Logger
import com.duq.android.network.withServerAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * REST-клиент нового ядра DUQ (duq-core за nginx, база [AppConfig.DUQ_API_BASE_URL]).
 * Чат-цикл: enqueue ([sendMessage]) → poll ([pollTask]) → итоговый ответ
 * ([awaitResponse]). История — [conversations]/[messages].
 *
 * Все запросы несут edge-токен (X-Auth-Token) через [withServerAuth]. Сетевые вызовы
 * выполняются на [Dispatchers.IO]; HTTP-клиент шарит пул соединений (один на инстанс).
 */
@Singleton
class DuqRestClient @Inject constructor(
    private val logger: Logger
) {
    companion object {
        private const val TAG = "DuqRest"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AppConfig.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(AppConfig.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    private fun url(path: String) = AppConfig.DUQ_API_BASE_URL + path

    // /conversations и /messages на ядре защищены HTTPBearer (Depends(keycloak_sub)),
    // в отличие от /message и /task. Они принимают тот же edge-токен, но в заголовке
    // Authorization: Bearer (не X-Auth-Token). Без него — 401 и пустая история.
    private fun Request.Builder.withBearer(): Request.Builder =
        if (AppConfig.SERVER_TOKEN.isNotEmpty())
            header("Authorization", "Bearer ${AppConfig.SERVER_TOKEN}")
        else this

    /** Ставит сообщение в очередь ядра. Возвращает task_id для последующего поллинга. */
    suspend fun sendMessage(text: String): String = withContext(Dispatchers.IO) {
        val body = gson.toJson(MessageRequest(text)).toRequestBody(JSON)
        val req = Request.Builder().url(url("message")).withServerAuth().post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw DuqApiException("message ${resp.code}: ${raw.take(200)}")
            val enqueued = gson.fromJson(raw, MessageEnqueued::class.java)
                ?: throw DuqApiException("message: empty body")
            logger.d(TAG, "enqueued task=${enqueued.taskId} status=${enqueued.status}")
            enqueued.taskId
        }
    }

    /** Один опрос состояния задачи. */
    suspend fun pollTask(taskId: String): TaskResult = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url("task/$taskId")).withServerAuth().get().build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw DuqApiException("task ${resp.code}: ${raw.take(200)}")
            gson.fromJson(raw, TaskResult::class.java)
                ?: throw DuqApiException("task: empty body")
        }
    }

    /**
     * Поллит задачу до терминального состояния и возвращает текст ответа.
     * Бросает [DuqApiException] на failed/таймауте.
     */
    suspend fun awaitResponse(
        taskId: String,
        timeoutMs: Long = AppConfig.DUQ_TASK_TIMEOUT_MS,
        intervalMs: Long = AppConfig.DUQ_TASK_POLL_INTERVAL_MS
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val result = pollTask(taskId)
            when {
                result.isCompleted -> return result.result?.response.orEmpty()
                result.isFailed -> throw DuqApiException("task failed: ${result.error ?: result.status}")
                else -> delay(intervalMs)
            }
        }
        throw DuqApiException("task $taskId: timeout after ${timeoutMs}ms")
    }

    /** Список диалогов пользователя. */
    suspend fun conversations(): List<ConversationDto> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url("conversations")).withServerAuth().withBearer().get().build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw DuqApiException("conversations ${resp.code}")
            val type = object : TypeToken<List<ConversationDto>>() {}.type
            gson.fromJson<List<ConversationDto>>(raw, type) ?: emptyList()
        }
    }

    /** Сообщения одного диалога. */
    suspend fun messages(convId: String): List<HistoryMsg> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url("conversations/$convId/messages")).withServerAuth().withBearer().get().build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw DuqApiException("messages ${resp.code}")
            val type = object : TypeToken<List<HistoryMsg>>() {}.type
            gson.fromJson<List<HistoryMsg>>(raw, type) ?: emptyList()
        }
    }
}

/** Ошибка вызова REST-API ядра DUQ. */
class DuqApiException(message: String) : Exception(message)
