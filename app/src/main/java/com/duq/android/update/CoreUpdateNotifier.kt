package com.duq.android.update

import android.content.Context
import com.duq.android.logging.FileLogger
import com.duq.android.network.CoreUpdateClient
import com.duq.android.service.DuqNotificationManager

/**
 * Фоновая проверка обновления ЯДРА DUQ: опрашивает бэкенд-ручку
 * `/core-update/status` и, если доступна новая версия, шлёт ЛОКАЛЬНЫЙ пуш
 * «Обновление ядра» с deep-link в раздел «Движок» (type="core_update").
 *
 * Дедуп по версии (prefs `notified_version`) — один пуш на версию, чтобы
 * периодический воркер (каждые 6ч) + проверка на старте app не спамили.
 * Зовётся из UpdateWorker и при старте MainActivity, рядом с APK-проверкой.
 */
object CoreUpdateNotifier {
    private const val PREFS = "duq_core_update"
    private const val KEY_NOTIFIED = "notified_version"
    private const val KEY_RESULT_TS = "result_ts_notified"

    /**
     * После апдейта ядра движок сам себя проверяет (readiness /health + health-monitor) и
     * пишет результат, который update_server отдаёт в `status.result`. Здесь показываем юзеру
     * уведомление ОДИН раз на результат (дедуп по `result.ts`): «Добро пожаловать в ядро X —
     * всё работает ✅» либо текст проблемы. Зовётся из поллинга Движка и из UpdateWorker —
     * кто первый поймал завершённый апдейт, тот и уведомит.
     */
    fun notifyResult(context: Context, status: CoreUpdateClient.Status) {
        val res = status.result ?: return
        if (status.running || res.ts.isBlank()) return            // апдейт ещё идёт / нет результата
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_RESULT_TS, null) == res.ts) return  // про этот результат уже уведомляли
        DuqNotificationManager(context).showMessageNotification(
            text = res.summary.ifBlank {
                if (res.ok) "Ядро обновлено до ${res.version ?: "?"} — всё работает ✅"
                else "Ядро обновлено до ${res.version ?: "?"}, но есть проблема — проверь Движок"
            },
            title = if (res.ok) "✅ Ядро обновлено" else "⚠️ Ядро: проблема после обновления",
            type = "core_update",
        )
        prefs.edit().putString(KEY_RESULT_TS, res.ts).apply()
        FileLogger(context).i("CoreUpdate", "уведомление о результате (ok=${res.ok}, v=${res.version}) отправлено")
    }

    suspend fun check(context: Context) {
        val flog = FileLogger(context)
        val status = CoreUpdateClient(context).status() ?: run {
            flog.i("CoreUpdate", "status недоступен — пропуск"); return
        }
        notifyResult(context, status)  // если апдейт только что завершился — уведомить о результате
        if (!status.updateAvailable || status.latest.isNullOrBlank()) {
            flog.i("CoreUpdate", "обновления ядра нет (current=${status.current} latest=${status.latest})")
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_NOTIFIED, null) == status.latest) {
            flog.i("CoreUpdate", "уже уведомляли про ${status.latest}"); return
        }
        DuqNotificationManager(context).showMessageNotification(
            text = "Доступна версия ядра ${status.latest} (сейчас ${status.current ?: "?"})",
            title = "Обновление ядра",
            type = "core_update",
        )
        prefs.edit().putString(KEY_NOTIFIED, status.latest).apply()
        flog.i("CoreUpdate", "пуш об обновлении ядра ${status.latest} отправлен")
    }
}
