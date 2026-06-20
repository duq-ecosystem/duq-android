package com.duq.android.network

import com.duq.android.config.AppConfig
import okhttp3.Request

/**
 * Добавляет edge-токен периметра (X-Auth-Token) в запрос/WS-хендшейк, если токен
 * задан в сборке. nginx проверяет его на входе; без него → 401 → fail2ban банит IP.
 * Единая точка — чтобы все серверные запросы (TTS/STT/core-update/gateway WS) шли
 * с токеном без дублирования. Пусто → заголовок не добавляется (отладка до гейта).
 */
fun Request.Builder.withServerAuth(): Request.Builder =
    if (AppConfig.SERVER_TOKEN.isNotEmpty())
        header(AppConfig.SERVER_TOKEN_HEADER, AppConfig.SERVER_TOKEN)
    else this
