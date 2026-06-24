package com.duq.android.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS-движок: Darwin (NSURLSession). DoH на iOS — через NWParameters PrivacyContext
 * (нативно), подключается на фазе DoH; на старте — системный резолвер.
 */
actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin) {
        block()
    }
