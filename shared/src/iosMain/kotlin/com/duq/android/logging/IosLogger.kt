package com.duq.android.logging

/**
 * iOS-реализация [Logger]. Пишет в stdout (виден в Xcode-консоли). Файловый лог
 * для отладки на устройстве переносится отдельно; предоставляется через DI на фазе DI.
 */
class IosLogger : Logger {
    private fun log(level: String, tag: String, message: String) = println("$level/$tag: $message")
    override fun v(tag: String, message: String) = log("V", tag, message)
    override fun d(tag: String, message: String) = log("D", tag, message)
    override fun i(tag: String, message: String) = log("I", tag, message)
    override fun w(tag: String, message: String) = log("W", tag, message)
    override fun w(tag: String, message: String, throwable: Throwable) =
        log("W", tag, "$message: ${throwable.message}")
    override fun e(tag: String, message: String) = log("E", tag, message)
    override fun e(tag: String, message: String, throwable: Throwable) =
        log("E", tag, "$message: ${throwable.message}")
}
