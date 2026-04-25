package com.duq.android.network

import com.duq.android.config.AppConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating configured OkHttpClient instances.
 * Extracted from DuqApiClient for SRP.
 */
interface HttpClientFactory {
    fun create(interceptor: TokenRefreshInterceptor? = null): OkHttpClient
}

@Singleton
class DefaultHttpClientFactory @Inject constructor() : HttpClientFactory {

    override fun create(interceptor: TokenRefreshInterceptor?): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(AppConfig.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(AppConfig.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .apply {
                interceptor?.let { addInterceptor(it) }
            }
            .build()
    }
}
