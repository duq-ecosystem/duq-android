package com.duq.android.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.duq.android.network.CoreUpdateClient
import com.duq.android.ui.theme.DuqColors

/**
 * Экран раздела Пульта. Типизированный рендер на РЕАЛЬНЫХ полях (сверены с живым
 * API на VPS) + действия по реальным RPC. Без заглушек: данные и действия живые.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(sectionKey: String, onBack: () -> Unit, vm: SectionViewModel = hiltViewModel()) {
    // «Движок» — особый раздел: обновление ядра идёт через бэкенд-ручку /core-update
    // (HTTP), а не WS-RPC. Рендерим выделенный экран и не трогаем общий WS-путь.
    if (sectionKey == "engine") { EngineScreen(vm, onBack); return }
    val spec = SECTION_RPC[sectionKey]
    val state by vm.state.collectAsState()
    LaunchedEffect(sectionKey) { spec?.let { vm.load(it) } }

    Scaffold(
        containerColor = DuqColors.background,
        topBar = {
            TopAppBar(
                title = { Text(spec?.title ?: sectionKey, color = DuqColors.textPrimary) },
                navigationIcon = {
                    Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Назад",
                        tint = DuqColors.textPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack)
                            .padding(horizontal = 16.dp, vertical = 8.dp).size(20.dp))
                },
                actions = {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить",
                        tint = DuqColors.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { spec?.let { vm.load(it) } }.padding(horizontal = 14.dp, vertical = 8.dp).size(22.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DuqColors.background),
                // внешний Scaffold уже отступил от статус-бара — не двоим
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is SectionViewModel.State.Loading ->
                    CircularProgressIndicator(color = DuqColors.primary, modifier = Modifier.align(Alignment.Center))
                is SectionViewModel.State.Error ->
                    Text("⚠️ ${s.message}", color = DuqColors.textMuted, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp))
                is SectionViewModel.State.Data -> SectionContent(sectionKey, s, vm)
            }
        }
    }
}

// «Движок» — обновление ядра через бэкенд-ручку /core-update (HTTP). Показывает
// текущую/доступную версию и кнопку «Обновить ядро» → вызывает безопасный
// update-openclaw.sh (память не трогает), НЕ gateway-update.run.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineScreen(vm: SectionViewModel, onBack: () -> Unit) {
    val core by vm.core.collectAsState()
    LaunchedEffect(Unit) { vm.loadCore() }
    // Пока апдейт идёт — авто-опрос статуса (живой прогресс + детект завершения),
    // чтобы не тапать refresh вручную. Эффект перезапускается на смене running:
    // running=true → цикл опроса; loadCore выставит running=false → цикл выходит.
    val running = (core as? SectionViewModel.CoreState.Data)?.status?.running == true
    LaunchedEffect(running) {
        while (running) {
            kotlinx.coroutines.delay(8000)
            vm.loadCore()
        }
    }
    Scaffold(
        containerColor = DuqColors.background,
        topBar = {
            TopAppBar(
                title = { Text("Движок", color = DuqColors.textPrimary) },
                navigationIcon = {
                    Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Назад",
                        tint = DuqColors.textPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack)
                            .padding(horizontal = 16.dp, vertical = 8.dp).size(20.dp))
                },
                actions = {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить",
                        tint = DuqColors.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { vm.loadCore() }.padding(horizontal = 14.dp, vertical = 8.dp).size(22.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DuqColors.background),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = core) {
                is SectionViewModel.CoreState.Loading ->
                    CircularProgressIndicator(color = DuqColors.primary, modifier = Modifier.align(Alignment.Center))
                is SectionViewModel.CoreState.Error ->
                    Text("⚠️ ${s.message}", color = DuqColors.textMuted, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp))
                is SectionViewModel.CoreState.Data ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) { item { EngineCard(s.status, vm) } }
            }
        }
    }
}

@Composable private fun EngineCard(st: CoreUpdateClient.Status, vm: SectionViewModel) = Card {
    var showConfirm by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Text("⚙️ Ядро OpenClaw", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DuqColors.textPrimary)
    // Пока апдейт идёт — НЕ показываем версии: package.json (источник current) npm
    // перезаписывает на новую ещё в процессе установки, хотя gateway всё ещё на старой
    // и не перезапущен → «текущая: <новая>» вводит в заблуждение, будто уже готово.
    // Версии показываем только в покое; во время апдейта — состояние «идёт».
    if (!st.running) {
        Text("текущая: ${st.current ?: "?"}", fontSize = 12.sp, color = DuqColors.textSecondary,
            modifier = Modifier.padding(top = 4.dp))
        if (!st.latest.isNullOrBlank() && st.latest != st.current)
            Text("доступна: ${st.latest}", fontSize = 12.sp, color = DuqColors.primary, modifier = Modifier.padding(top = 2.dp))
        if (st.updateAvailable)
            Text("⚡ Доступно обновление", fontSize = 12.sp, color = DuqColors.success, modifier = Modifier.padding(top = 2.dp))
    }
    Spacer(Modifier.height(10.dp))
    if (st.running) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = DuqColors.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Обновляется до ${st.latest ?: "новой версии"}… (~8-10 мин)", fontSize = 13.sp, color = DuqColors.textSecondary)
        }
        // Живой хвост лога апдейта — обратная связь во время долгой операции.
        if (st.log.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                st.log.trim().lines().takeLast(6).joinToString("\n"),
                fontSize = 10.sp,
                color = DuqColors.textMuted,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    } else if (st.updateAvailable) {
        // кнопка ТОЛЬКО когда есть что обновлять; на последней версии её быть не должно.
        ActionChip("Обновить ядро", DuqColors.primary) { showConfirm = true }
    } else {
        Text("✓ Установлена последняя версия", fontSize = 13.sp, color = DuqColors.success)
    }
    msg?.let { Text(it, fontSize = 12.sp, color = DuqColors.textSecondary, modifier = Modifier.padding(top = 8.dp)) }

    if (showConfirm) AlertDialog(
        onDismissRequest = { showConfirm = false },
        title = { Text("Обновить ядро?") },
        text = { Text("Ядро обновится до новой версии. Бот перезапустится и будет недоступен несколько минут. Продолжить?") },
        confirmButton = {
            TextButton(onClick = {
                showConfirm = false
                vm.runCore { res -> msg = when (res) {
                    CoreUpdateClient.RunResult.STARTED -> "Обновление запущено…"
                    CoreUpdateClient.RunResult.ALREADY_RUNNING -> "Обновление уже идёт"
                    CoreUpdateClient.RunResult.FAILED -> "Не удалось запустить (бэкенд недоступен?)"
                } }
            }) { Text("Обновить", color = DuqColors.primary) }
        },
        dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Отмена") } },
        containerColor = DuqColors.surfaceVariant
    )
}

@Composable
private fun SectionContent(key: String, data: SectionViewModel.State.Data, vm: SectionViewModel) {
    val rows = data.rows
    if (rows.isEmpty() && data.meta.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Пусто", color = DuqColors.textMuted)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        when (key) {
            "agents" -> items(rows) { AgentCard(it) }
            "models" -> items(rows) { ModelCard(it) }
            "cron" -> items(rows) { CronCard(it, vm) }
            "tasks" -> items(rows) { TaskCard(it, vm) }
            "nodes" -> items(rows) { NodeCard(it, vm) }
            "skills" -> items(rows) { SkillCard(it) }
            // doctor.memory.status в WS — объект без списка (rows пуст) → карточка из raw.
            "memory" -> if (rows.isNotEmpty()) items(rows) { MemoryCard(it, vm) } else item { MemoryCard(data.raw, vm) }
            "voice" -> items(listOf(0)) { VoicePanel(data.meta, vm) }
            // channels / usage / engine / tools — status-payload произвольной формы.
            // Разворачиваем ВЕСЬ payload в читаемые строки key→value (вложенные объекты
            // на один уровень, списки как «N элем.»), скрывая шум. Это надёжнее, чем
            // GenericCard по rows: тот давал «—» на безымянных строках (Расходы).
            else -> items(flattenStatus(data.raw)) { (k, v) -> KvCard(k, v) }
        }
    }
}

// ---- helpers ----
private fun str(m: Map<String, Any?>, vararg keys: String): String {
    for (k in keys) m[k]?.let { if (it.toString().isNotBlank()) return it.toString() }
    return ""
}

// Числа из JSON прилетают как Double (1.78e12) — показываем как целое, не «1.78E12».
// Значения, похожие на epoch-миллисекунды (13 цифр, ~2001..2033), форматируем как дату.
private val tsFmt = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US)
    .apply { timeZone = java.util.TimeZone.getTimeZone(com.duq.android.config.AppConfig.LOG_TIMEZONE) }
private fun fmtVal(v: Any?): String = when (v) {
    is Double -> {
        val l = v.toLong()
        if (l in 1_000_000_000_000L..2_000_000_000_000L) tsFmt.format(java.util.Date(l))
        else if (v == l.toDouble()) l.toString() else v.toString()
    }
    is Long -> if (v in 1_000_000_000_000L..2_000_000_000_000L) tsFmt.format(java.util.Date(v)) else v.toString()
    else -> v.toString()
}

// Сжать вложенный объект в читаемую строку «k=v · k=v» (только скалярные подполя).
private fun summarize(v: Any?): String = when (v) {
    is Map<*, *> -> v.entries
        .filter { it.value != null && it.value !is Map<*, *> && it.value !is List<*> }
        .joinToString(" · ") { "${it.key}=${fmtVal(it.value)}" }
        .ifBlank { "${v.size} полей" }
    is List<*> -> "${v.size} элем."
    else -> fmtVal(v)
}

// Разворачивает status-payload в плоские строки key→value для KvCard: вложенные
// объекты разбираются на один уровень (channels.telegram = …), шум скрыт.
// Чинит «пустые/мусорные» status-разделы (Интеграции/Расходы/Движок), где полезные
// данные лежат во вложенных объектах, а ViewModel.meta их отбрасывает.
private val STATUS_SKIP = setOf(
    "ts", "type", "agentId", "channelOrder", "channelLabels",
    "channelDetailLabels", "channelSystemImages", "channelMeta"
)
private fun flattenStatus(raw: Map<String, Any?>): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    for ((k, v) in raw) {
        if (k in STATUS_SKIP || v == null) continue
        when (v) {
            is Map<*, *> -> {
                if (v.isEmpty()) continue
                @Suppress("UNCHECKED_CAST")
                (v as Map<Any?, Any?>).forEach { (sk, sv) -> out += "$k.$sk" to summarize(sv) }
            }
            is List<*> -> {
                // Список объектов (providers, accounts…) разворачиваем по элементам,
                // чтобы видеть сами данные, а не «N элем.». Большие списки — ограничиваем.
                if (v.isNotEmpty() && v.all { it is Map<*, *> }) {
                    v.take(12).forEachIndexed { i, item -> out += "$k[$i]" to summarize(item) }
                    if (v.size > 12) out += k to "…ещё ${v.size - 12}"
                } else out += k to "${v.size} элем."
            }
            else -> out += k to fmtVal(v)
        }
    }
    return out
}
@Suppress("UNCHECKED_CAST")
private fun sub(m: Map<String, Any?>, key: String): Map<String, Any?>? = m[key] as? Map<String, Any?>
private fun bool(m: Map<String, Any?>, key: String): Boolean = (m[key] as? Boolean) ?: false

// ---- typed cards (реальные поля) ----

@Composable private fun AgentCard(m: Map<String, Any?>) = Card {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(str(m, "identityEmoji").ifBlank { "🤖" }, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(str(m, "id"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DuqColors.textPrimary)
        if (bool(m, "isDefault")) { Spacer(Modifier.width(8.dp)); Badge("default", DuqColors.primary) }
    }
    // model в WS приходит объектом {primary, fallbacks} (не строкой как в CLI).
    val model = sub(m, "model")?.get("primary")?.toString() ?: str(m, "model")
    Text(model, fontSize = 13.sp, color = DuqColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
    @Suppress("UNCHECKED_CAST")
    (sub(m, "model")?.get("fallbacks") as? List<Any?>)?.takeIf { it.isNotEmpty() }?.let {
        Text("fallback: ${it.joinToString(", ")}", fontSize = 11.sp, color = DuqColors.textMuted)
    }
}

@Composable private fun ModelCard(m: Map<String, Any?>) = Card {
    Text(str(m, "name", "key"), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DuqColors.textPrimary)
    Text(str(m, "key"), fontSize = 12.sp, color = DuqColors.textMuted)
    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(str(m, "input"), fontSize = 12.sp, color = DuqColors.textSecondary)
        (m["contextWindow"] as? Number)?.let { Text("ctx ${it.toInt() / 1000}k", fontSize = 12.sp, color = DuqColors.textSecondary) }
        @Suppress("UNCHECKED_CAST")
        (m["tags"] as? List<Any?>)?.forEach { Badge(it.toString(), DuqColors.accent) }
    }
}

@Composable private fun CronCard(m: Map<String, Any?>, vm: SectionViewModel) = Card {
    val id = str(m, "id"); val enabled = bool(m, "enabled")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(str(m, "name", "id"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DuqColors.textPrimary,
            modifier = Modifier.weight(1f))
        Badge(if (enabled) "вкл" else "выкл", if (enabled) DuqColors.success else DuqColors.textMuted)
    }
    str(m, "description").takeIf { it.isNotBlank() }?.let {
        Text(it, fontSize = 12.sp, color = DuqColors.textSecondary, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
    }
    Text("⏱ ${sub(m, "schedule")?.get("expr") ?: "—"}   ·   ${str(m, "status")}",
        fontSize = 12.sp, color = DuqColors.textMuted, modifier = Modifier.padding(top = 4.dp))
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionChip("Запустить", DuqColors.primary) { vm.action("cron.run", mapOf("id" to id)) }
        ActionChip(if (enabled) "Выключить" else "Включить", DuqColors.textSecondary) {
            vm.action("cron.update", mapOf("id" to id, "enabled" to !enabled))
        }
    }
}

@Composable private fun TaskCard(m: Map<String, Any?>, vm: SectionViewModel) = Card {
    val status = str(m, "status")
    Text(str(m, "task", "taskId"), fontSize = 14.sp, color = DuqColors.textPrimary, maxLines = 2)
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge(status, statusColor(status)); Text(str(m, "runtime"), fontSize = 12.sp, color = DuqColors.textMuted)
        }
        if (status == "queued" || status == "running") {
            ActionChip("Отменить", DuqColors.error) { vm.action("tasks.cancel", mapOf("id" to str(m, "taskId"))) }
        }
    }
}

@Composable private fun NodeCard(m: Map<String, Any?>, vm: SectionViewModel) = Card {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("📱", fontSize = 18.sp); Spacer(Modifier.width(8.dp))
        Text(str(m, "displayName", "nodeId"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = DuqColors.textPrimary, modifier = Modifier.weight(1f))
        Badge(if (bool(m, "connected")) "online" else "offline", if (bool(m, "connected")) DuqColors.success else DuqColors.textMuted)
    }
    Text("${str(m, "platform")} · ${str(m, "version")}", fontSize = 12.sp, color = DuqColors.textSecondary,
        modifier = Modifier.padding(top = 4.dp))
    @Suppress("UNCHECKED_CAST")
    (m["caps"] as? List<Any?>)?.let {
        Text("умеет: ${it.joinToString(", ")}", fontSize = 12.sp, color = DuqColors.textMuted, modifier = Modifier.padding(top = 2.dp))
    }
    // pending-нода (ещё не одобрена) → кнопка одобрить.
    if (!bool(m, "paired") && str(m, "nodeId").isNotBlank()) {
        ActionChip("Одобрить", DuqColors.success) { vm.action("node.pair.approve", mapOf("nodeId" to str(m, "nodeId"))) }
    }
}

@Composable private fun SkillCard(m: Map<String, Any?>) = Card {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(str(m, "emoji").ifBlank { "✨" }, fontSize = 18.sp); Spacer(Modifier.width(8.dp))
        Text(str(m, "name"), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DuqColors.textPrimary, modifier = Modifier.weight(1f))
        when {
            bool(m, "disabled") -> Badge("off", DuqColors.textMuted)
            bool(m, "eligible") -> Badge("готов", DuqColors.success)
            else -> Badge("нет зависимостей", DuqColors.warning)
        }
    }
    str(m, "description").takeIf { it.isNotBlank() }?.let {
        Text(it, fontSize = 12.sp, color = DuqColors.textSecondary, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun MemoryCard(m: Map<String, Any?>, vm: SectionViewModel) = Card {
    val aid = str(m, "agentId").ifBlank { "main" }
    Text("🧩 память · $aid", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DuqColors.textPrimary)
    val st = sub(m, "status")
    if (st != null && st["files"] != null) {
        // CLI-форма (массив с status.files/chunks/backend) — на случай если придёт списком.
        Text("файлов: ${st["files"]} · чанков: ${st["chunks"]} · ${st["backend"] ?: ""}",
            fontSize = 12.sp, color = DuqColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
    } else {
        // WS-форма doctor.memory.status: provider + embedding{ok} + dreaming{enabled,storageMode,shortTermEntries}.
        str(m, "provider").takeIf { it.isNotBlank() }?.let {
            Text("провайдер: $it", fontSize = 12.sp, color = DuqColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        sub(m, "embedding")?.let { emb ->
            val ok = (emb["ok"] as? Boolean) == true
            Text("эмбеддинги: ${if (ok) "ок" else "не готовы"}",
                fontSize = 12.sp, color = if (ok) DuqColors.success else DuqColors.textMuted, modifier = Modifier.padding(top = 2.dp))
        }
        sub(m, "dreaming")?.let { dr ->
            val en = (dr["enabled"] as? Boolean) == true
            val mode = dr["storageMode"]?.toString().orEmpty()
            val shortN = (dr["shortTermEntries"] as? List<*>)?.size ?: 0
            Text("dreaming: ${if (en) "вкл" else "выкл"}${if (mode.isNotBlank()) " · $mode" else ""} · краткосрочно: $shortN",
                fontSize = 12.sp, color = DuqColors.textSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionChip("Repair", DuqColors.textSecondary) { vm.action("doctor.memory.repairDreamingArtifacts", mapOf("agentId" to aid)) }
        ActionChip("Dedupe", DuqColors.textSecondary) { vm.action("doctor.memory.dedupeDreamDiary", mapOf("agentId" to aid)) }
    }
}

@Composable private fun VoicePanel(meta: Map<String, Any?>, vm: SectionViewModel) = Card {
    val enabled = (meta["enabled"] as? Boolean) ?: (meta["enabled"].toString() == "true")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🎙 TTS", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DuqColors.textPrimary, modifier = Modifier.weight(1f))
        Badge(if (enabled) "вкл" else "выкл", if (enabled) DuqColors.success else DuqColors.textMuted)
    }
    meta.entries.filter { it.key != "enabled" }.forEach {
        Text("${it.key}: ${it.value}", fontSize = 12.sp, color = DuqColors.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionChip(if (enabled) "Выключить" else "Включить", DuqColors.primary) {
            vm.action(if (enabled) "tts.disable" else "tts.enable")
        }
    }
}

@Composable private fun KvCard(k: String, v: String) = Card {
    Text(k, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DuqColors.textPrimary)
    Text(v.take(120), fontSize = 13.sp, color = DuqColors.textSecondary, modifier = Modifier.padding(top = 2.dp))
}

@Composable private fun GenericCard(m: Map<String, Any?>) = Card {
    Text(str(m, "title", "name", "id", "key", "label").ifBlank { "—" },
        fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DuqColors.textPrimary)
    str(m, "status", "state", "description", "type").takeIf { it.isNotBlank() }?.let {
        Text(it, fontSize = 13.sp, color = DuqColors.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

// ---- shared UI ----
@Composable private fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DuqColors.surfaceVariant)
        .border(1.dp, DuqColors.glassBorder, RoundedCornerShape(12.dp)).padding(14.dp), content = content)

@Composable private fun Badge(text: String, color: Color) = Text(
    text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color,
    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp))

@Composable private fun ActionChip(label: String, color: Color, onClick: () -> Unit) = Text(
    label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color,
    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))

private fun statusColor(s: String): Color = when (s) {
    "succeeded", "idle" -> DuqColors.success
    "failed", "timed_out", "lost" -> DuqColors.error
    "running", "queued" -> DuqColors.primary
    else -> DuqColors.textMuted
}
