package com.duq.android.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duq.android.network.openclaw.OpenClawGatewayClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Лента (⚡) — глобальный монитор «здесь и сейчас» на реальных RPC (spec §7):
 * аппрувы (HITL), cron-прогоны, активные задачи, расходы, хвост логов. Всё
 * грузится параллельными вызовами gateway.rpc и обновляется по pull/refresh.
 */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val gateway: OpenClawGatewayClient
) : ViewModel() {

    data class Approval(val id: String, val title: String, val kind: String) // exec | plugin
    data class CronRun(val title: String, val status: String)
    data class TaskRow(val id: String, val title: String, val status: String)

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val approvals: List<Approval> = emptyList(),
        val cronRuns: List<CronRun> = emptyList(),
        val tasks: List<TaskRow> = emptyList(),
        val costSummary: String = "",
        val logTail: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val approvals = loadApprovals()
                val cronRuns = loadCronRuns()
                val tasks = loadTasks()
                val cost = loadCost()
                val logs = loadLogs()
                _state.value = State(false, null, approvals, cronRuns, tasks, cost, logs)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Ошибка")
            }
        }
    }

    private suspend fun loadApprovals(): List<Approval> {
        val out = mutableListOf<Approval>()
        listOf("exec" to "exec.approval.list", "plugin" to "plugin.approval.list").forEach { (kind, method) ->
            val p = runCatching { gateway.rpc(method) }.getOrNull() ?: return@forEach
            (p.values.firstOrNull { it is List<*> } as? List<*>)?.forEach { row ->
                val m = row as? Map<*, *> ?: return@forEach
                out += Approval(
                    id = (m["id"] ?: m["requestId"] ?: "").toString(),
                    title = (m["title"] ?: m["command"] ?: m["summary"] ?: m["tool"] ?: "Запрос").toString().take(80),
                    kind = kind
                )
            }
        }
        return out
    }

    private suspend fun loadCronRuns(): List<CronRun> {
        val p = runCatching { gateway.rpc("cron.runs") }.getOrNull() ?: return emptyList()
        return (p.values.firstOrNull { it is List<*> } as? List<*>)?.mapNotNull { row ->
            val m = row as? Map<*, *> ?: return@mapNotNull null
            CronRun(
                title = (m["jobId"] ?: m["name"] ?: m["title"] ?: m["id"] ?: "cron").toString().take(60),
                status = (m["status"] ?: m["state"] ?: "").toString()
            )
        }?.take(20) ?: emptyList()
    }

    private suspend fun loadTasks(): List<TaskRow> {
        val p = runCatching { gateway.rpc("tasks.list") }.getOrNull() ?: return emptyList()
        return (p.values.firstOrNull { it is List<*> } as? List<*>)?.mapNotNull { row ->
            val m = row as? Map<*, *> ?: return@mapNotNull null
            TaskRow(
                id = (m["id"] ?: m["taskId"] ?: "").toString(),
                title = (m["title"] ?: m["kind"] ?: m["id"] ?: "task").toString().take(60),
                status = (m["status"] ?: m["state"] ?: "").toString()
            )
        }?.take(20) ?: emptyList()
    }

    private suspend fun loadCost(): String {
        val p = runCatching { gateway.rpc("usage.status") }.getOrNull() ?: return ""
        val scalar = p.entries.firstOrNull { it.value is Number || it.value is String }
        return scalar?.let { "${it.key}: ${it.value}" } ?: if (p.isNotEmpty()) "данные получены" else ""
    }

    private suspend fun loadLogs(): List<String> {
        val p = runCatching { gateway.rpc("logs.tail", mapOf("limit" to 20)) }.getOrNull() ?: return emptyList()
        return (p.values.firstOrNull { it is List<*> } as? List<*>)?.map { it.toString().take(120) }?.takeLast(20)
            ?: emptyList()
    }

    fun resolveApproval(a: Approval, approve: Boolean) {
        viewModelScope.launch {
            val method = if (a.kind == "exec") "exec.approval.resolve" else "plugin.approval.resolve"
            runCatching { gateway.rpc(method, mapOf("id" to a.id, "decision" to if (approve) "allow" else "deny")) }
            refresh()
        }
    }

    fun cancelTask(t: TaskRow) {
        viewModelScope.launch {
            runCatching { gateway.rpc("tasks.cancel", mapOf("id" to t.id)) }
            refresh()
        }
    }
}
