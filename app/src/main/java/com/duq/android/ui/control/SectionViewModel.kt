package com.duq.android.ui.control

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duq.android.network.CoreUpdateClient
import com.duq.android.network.openclaw.OpenClawGatewayClient
import com.duq.android.update.CoreUpdateNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Что вызвать у gateway для раздела Пульта (реальные методы, проверены на VPS). */
data class SectionSpec(val title: String, val method: String, val params: Map<String, Any?>? = null)

val SECTION_RPC: Map<String, SectionSpec> = mapOf(
    "agents" to SectionSpec("Агенты", "agents.list"),
    "models" to SectionSpec("Модели", "models.list", mapOf("view" to "configured")),
    "cron" to SectionSpec("Расписания", "cron.list"),
    "tasks" to SectionSpec("Задачи", "tasks.list"),
    "skills" to SectionSpec("Навыки", "skills.status"),
    "tools" to SectionSpec("Инструменты", "tools.catalog"),
    "channels" to SectionSpec("Интеграции", "channels.status"),
    "memory" to SectionSpec("Память", "doctor.memory.status"),
    "usage" to SectionSpec("Расходы", "usage.status"),
    "voice" to SectionSpec("Голос", "tts.status"),
    "nodes" to SectionSpec("Устройства", "node.list"),
    "engine" to SectionSpec("Движок", "status"),
)

/**
 * Грузит раздел Пульта и хранит СЫРЫЕ строки (List<Map>) — типизированный рендер
 * читает реальные поля прямо в Composable (поля сверены с живым API на VPS).
 * Действия ([action]) бьют по реальным RPC и перезагружают раздел.
 */
@HiltViewModel
class SectionViewModel @Inject constructor(
    private val gateway: OpenClawGatewayClient,
    private val coreUpdate: CoreUpdateClient,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // --- «Движок»: обновление ядра через бэкенд-ручку /core-update (HTTP, не WS).
    //     update.run у gateway ломает память — поэтому ТОЛЬКО наша ручка. ---
    sealed class CoreState {
        object Loading : CoreState()
        data class Error(val message: String) : CoreState()
        data class Data(val status: CoreUpdateClient.Status) : CoreState()
    }

    private val _core = MutableStateFlow<CoreState>(CoreState.Loading)
    val core: StateFlow<CoreState> = _core.asStateFlow()

    /** Подтянуть текущую/доступную версию ядра + флаг «идёт обновление». */
    fun loadCore() {
        // ⛔ Loading показываем ТОЛЬКО при первой загрузке. Авто-поллинг (каждые 8с во время
        // апдейта) НЕ должен сбрасывать в Loading — иначе экран мигает/«перезагружается».
        // Уже есть Data → обновляем на месте, плавно.
        if (_core.value !is CoreState.Data) _core.value = CoreState.Loading
        viewModelScope.launch {
            val s = coreUpdate.status()
            if (s != null) {
                // апдейт только что завершился? движок написал self-check → уведомить юзера
                // (дедуп по result.ts, покажется один раз): «добро пожаловать / ошибка».
                CoreUpdateNotifier.notifyResult(appContext, s)
                _core.value = CoreState.Data(s)
            } else if (_core.value !is CoreState.Data) {
                // транзиентный сбой поллинга при уже показанных данных — НЕ мигаем ошибкой,
                // оставляем последнюю Data; ошибку показываем только если данных ещё не было.
                _core.value = CoreState.Error("Бэкенд обновления недоступен")
            }
        }
    }

    /** Запустить обновление ядра через ручку (детач на сервере), затем обновить статус. */
    fun runCore(onResult: (CoreUpdateClient.RunResult) -> Unit = {}) {
        viewModelScope.launch {
            val res = coreUpdate.run()
            // дать серверу проставить running, затем перечитать статус
            loadCore()
            onResult(res)
        }
    }

    sealed class State {
        object Loading : State()
        data class Error(val message: String) : State()
        // rows — список сущностей (agents/cron/...); meta — скаляры payload (status-разделы);
        // raw — весь payload как есть (для status-разделов без списка, напр. doctor.memory.status,
        // где полезные данные лежат во вложенных объектах embedding/dreaming).
        data class Data(val rows: List<Map<String, Any?>>, val meta: Map<String, Any?>, val raw: Map<String, Any?>) : State()
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()
    private var lastSpec: SectionSpec? = null

    fun load(spec: SectionSpec) {
        lastSpec = spec
        _state.value = State.Loading
        viewModelScope.launch {
            try {
                val payload = gateway.rpc(spec.method, spec.params)
                if (payload.isEmpty()) {
                    _state.value = State.Error("Нет ответа от gateway (не подключён / нет прав)")
                    return@launch
                }
                // Самый большой список в payload — это «строки» раздела (для node.list
                // payload={pending:[],paired:[...]} → paired побеждает пустой pending).
                val rows = payload.values.filterIsInstance<List<*>>()
                    .maxByOrNull { it.size }
                    ?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
                val meta = payload.filterValues { it != null && it !is List<*> && it !is Map<*, *> }
                _state.value = State.Data(rows, meta, payload)
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Ошибка")
            }
        }
    }

    /** Выполнить действие раздела (реальный RPC) и перезагрузить. */
    fun action(method: String, params: Map<String, Any?>? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { gateway.rpc(method, params) }
            lastSpec?.let { load(it) }
            onDone()
        }
    }
}
