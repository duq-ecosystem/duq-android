package com.duq.android.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android-движок: OkHttp. DoH (обход «Unable to resolve host») подключается на фазе
 * DoH через engine { /* dns(DohDns) */ } — bootstrap-резолвер Cloudflare.
 */
actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        block()
    }
