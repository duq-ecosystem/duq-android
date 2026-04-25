package com.duq.android.service

import com.duq.android.error.DuqError
import com.duq.android.network.DuqApiClient
import javax.inject.Inject

/**
 * Maps exceptions and API errors to DuqError types.
 * Extracted from VoiceCommandProcessor for SRP.
 */
interface ErrorMapper {
    fun mapException(e: Exception): DuqError
    fun mapApiError(result: DuqApiClient.SendResult.Error): DuqError
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

    override fun mapApiError(result: DuqApiClient.SendResult.Error): DuqError {
        return when {
            result.code == 401 || result.code == 403 -> DuqError.AuthError.invalidToken()
            result.code != null && result.code >= 500 -> DuqError.NetworkError.serverError(result.code, result.message)
            result.code != null -> DuqError.NetworkError.clientError(result.code, result.message)
            else -> DuqError.NetworkError(result.message, result.code)
        }
    }
}
