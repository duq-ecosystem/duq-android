package com.duq.android.service

import com.duq.android.error.DuqError
import javax.inject.Inject

/**
 * Maps exceptions to DuqError types.
 * Extracted from VoiceCommandProcessor for SRP.
 */
interface ErrorMapper {
    fun mapException(e: Exception): DuqError
}

class DefaultErrorMapper @Inject constructor() : ErrorMapper {

    override fun mapException(e: Exception): DuqError {
        return when (e) {
            is java.net.SocketTimeoutException -> DuqError.NetworkError.timeout()
            is java.net.UnknownHostException -> DuqError.NetworkError.noConnection()
            is java.net.ConnectException -> DuqError.NetworkError.noConnection("Connection refused: ${e.message}")
            is java.io.IOException -> DuqError.NetworkError(e.message ?: "Network error")
            else -> DuqError.AudioError(e.message ?: "Unknown error", e)
        }
    }
}
