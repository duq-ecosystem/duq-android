package com.duq.android.ui.control

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duq.android.network.CoreUpdateClient
import com.duq.android.update.CoreUpdateNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Пульт ядра DUQ — только карточка «Движок».
 *
 * Прежние gateway-RPC разделы (agents/models/cron/skills/tools/channels/memory/
 * usage/voice/nodes) убраны: у ядра DUQ нет gateway-RPC.
 * Полноценная админка ядра — отдельная фича под будущий core admin-REST. Остаётся
 * «Движок»: версия ядра + обновление через бэкенд-ручку /core-update (HTTP, не WS).
 */
@HiltViewModel
class SectionViewModel @Inject constructor(
    private val coreUpdate: CoreUpdateClient,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    sealed class CoreState {
        object Loading : CoreState()
        data class Error(val message: String) : CoreState()
        data class Data(val status: CoreUpdateClient.Status) : CoreState()
    }

    private val _core = MutableStateFlow<CoreState>(CoreState.Loading)
    val core: StateFlow<CoreState> = _core.asStateFlow()

    /** Подтянуть текущую/доступную ревизию ядра + флаг «идёт обновление». */
    fun loadCore() {
        // ⛔ Loading показываем ТОЛЬКО при первой загрузке. Авто-поллинг (каждые 8с во время
        // апдейта) НЕ должен сбрасывать в Loading — иначе экран мигает/«перезагружается».
        if (_core.value !is CoreState.Data) _core.value = CoreState.Loading
        viewModelScope.launch {
            val s = coreUpdate.status()
            if (s != null) {
                // апдейт только что завершился? бэкенд написал self-check → уведомить юзера
                // (дедуп по result.ts): «добро пожаловать / ошибка».
                CoreUpdateNotifier.notifyResult(appContext, s)
                _core.value = CoreState.Data(s)
            } else if (_core.value !is CoreState.Data) {
                _core.value = CoreState.Error("Бэкенд обновления недоступен")
            }
        }
    }

    /** Запустить обновление ядра через ручку (детач на сервере), затем обновить статус. */
    fun runCore(onResult: (CoreUpdateClient.RunResult) -> Unit = {}) {
        viewModelScope.launch {
            val res = coreUpdate.run()
            loadCore()
            onResult(res)
        }
    }
}
