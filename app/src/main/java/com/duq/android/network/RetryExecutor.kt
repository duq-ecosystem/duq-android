package com.duq.android.network

import android.util.Log
import com.duq.android.config.AppConfig
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes operations with exponential backoff retry logic.
 * Extracted from DuqApiClient for SRP.
 */
interface RetryExecutor {
    suspend fun <T> execute(
        maxRetries: Int = AppConfig.MAX_RETRIES,
        initialDelayMs: Long = AppConfig.INITIAL_RETRY_DELAY_MS,
        maxDelayMs: Long = AppConfig.MAX_RETRY_DELAY_MS,
        backoffMultiplier: Double = AppConfig.RETRY_MULTIPLIER,
        block: suspend () -> T
    ): T
}

@Singleton
class DefaultRetryExecutor @Inject constructor() : RetryExecutor {

    companion object {
        private const val TAG = "RetryExecutor"
    }

    override suspend fun <T> execute(
        maxRetries: Int,
        initialDelayMs: Long,
        maxDelayMs: Long,
        backoffMultiplier: Double,
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

    private fun isRetryable(e: Exception): Boolean {
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
}
