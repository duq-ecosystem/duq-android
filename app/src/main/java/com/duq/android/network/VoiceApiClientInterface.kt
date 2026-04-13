package com.duq.android.network

import java.io.File

interface VoiceApiClientInterface {
    suspend fun sendVoiceCommand(
        serverUrl: String,
        authToken: String,
        audioFile: File
    ): DuqApiClient.ApiResult
}
