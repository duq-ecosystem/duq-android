package com.duq.android.logging

/**
 * Multiplatform logger interface — развязка от платформенного лога.
 * Реализации: androidMain FileLogger (файл на устройстве), iosMain (NSLog/файл).
 */
interface Logger {
    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable)
    fun e(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable)
}
