package com.duq.android.ui.control

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.duq.android.network.duq.CronTaskDto
import com.duq.android.network.duq.SkillDto
import com.duq.android.ui.theme.DuqColors

/* ════════════════════ ЭКРАН «СКИЛЛЫ» ════════════════════ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(onBack: () -> Unit, vm: AutomationViewModel = hiltViewModel()) {
    val st by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SkillDto?>(null) }

    Scaffold(
        containerColor = DuqColors.background,
        topBar = { AutoTopBar("Скиллы", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; creating = true },
                containerColor = DuqColors.primary, contentColor = DuqColors.background,
                icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Скилл") }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                st.loading && st.skills.isEmpty() -> Loading()
                st.skills.isEmpty() -> EmptyState(Icons.Outlined.AutoAwesome,
                    "Скиллов нет", "Скилл — это md-промпт, который агент выполняет своими тулами.")
                else -> LazyColumn(
                    Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    st.error?.let { item { ErrorRow(it) } }
                    items(st.skills, key = { it.name }) { s ->
                        SkillItem(s, onTap = { editing = s }, onDelete = { vm.deleteSkill(s.name) })
                    }
                }
            }
        }
    }
    if (creating || editing != null) SkillSheet(
        initial = editing,
        onDismiss = { creating = false; editing = null },
        onSave = { name, desc, content ->
            if (editing == null) vm.createSkill(name, content, desc)
            else vm.editSkill(editing!!.name, content, desc)
            creating = false; editing = null
        }
    )
}

@Composable
private fun SkillItem(s: SkillDto, onTap: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth().clickable(onClick = onTap),
        colors = CardDefaults.elevatedCardColors(containerColor = DuqColors.surfaceVariant)
    ) {
        ListItem(
            headlineContent = { Text(s.name, color = DuqColors.textPrimary, fontWeight = FontWeight.Medium) },
            supportingContent = {
                Text(s.description?.takeIf { it.isNotBlank() } ?: s.content.take(80),
                    color = DuqColors.textSecondary, fontSize = 13.sp, maxLines = 2)
            },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, "Удалить", tint = DuqColors.accent)
                }
            },
            colors = ListItemDefaults.colors(containerColor = DuqColors.surfaceVariant)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillSheet(initial: SkillDto?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editMode = initial != null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = DuqColors.surfaceElevated) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (editMode) "Скилл: ${initial!!.name}" else "Новый скилл",
                color = DuqColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (!editMode) AutoField(name, { name = it }, "Имя (kebab-case)")
            AutoField(desc, { desc = it }, "Описание (одна строка)")
            AutoField(content, { content = it }, "md-промпт: что сделать", minLines = 5)
            Button(
                onClick = { if (name.isNotBlank() && content.isNotBlank()) onSave(name, desc, content) },
                enabled = name.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DuqColors.primary, contentColor = DuqColors.background),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (editMode) "Сохранить" else "Создать скилл") }
        }
    }
}

/* ════════════════════ ЭКРАН «РАСПИСАНИЕ» (крон) ════════════════════ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit, vm: AutomationViewModel = hiltViewModel()) {
    val st by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CronTaskDto?>(null) }

    Scaffold(
        containerColor = DuqColors.background,
        topBar = { AutoTopBar("Расписание", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; creating = true },
                containerColor = DuqColors.primary, contentColor = DuqColors.background,
                icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Задача") }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                st.loading && st.tasks.isEmpty() -> Loading()
                st.tasks.isEmpty() -> EmptyState(Icons.Outlined.Schedule,
                    "Задач нет", "Задача запускает скилл по расписанию (cron).")
                else -> LazyColumn(
                    Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    st.error?.let { item { ErrorRow(it) } }
                    items(st.tasks, key = { it.task_id }) { t ->
                        CronItem(t, onTap = { editing = t },
                            onToggle = { vm.toggleTask(t) }, onDelete = { vm.deleteTask(t.task_id) })
                    }
                }
            }
        }
    }
    if (creating || editing != null) CronSheet(
        initial = editing,
        skills = st.skills.map { it.name },
        onDismiss = { creating = false; editing = null },
        onSave = { n, cron, skill ->
            if (editing == null) vm.createTask(n, cron, skill)
            else vm.editTask(editing!!.task_id, n, cron, skill)
            creating = false; editing = null
        }
    )
}

@Composable
private fun CronItem(t: CronTaskDto, onTap: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth().clickable(onClick = onTap),
        colors = CardDefaults.elevatedCardColors(containerColor = DuqColors.surfaceVariant)
    ) {
        ListItem(
            headlineContent = {
                Text(t.name ?: t.skill ?: "—", color = DuqColors.textPrimary, fontWeight = FontWeight.Medium)
            },
            supportingContent = {
                Column {
                    Text("${cronHuman(t.cron)}  ·  ${t.skill ?: "—"}", color = DuqColors.textSecondary, fontSize = 13.sp)
                    t.next_run?.let { Text("след.: ${prettyNext(it)}", color = DuqColors.textDim, fontSize = 11.sp) }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = t.enabled, onCheckedChange = { onToggle() })
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, "Удалить", tint = DuqColors.accent)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = DuqColors.surfaceVariant)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CronSheet(
    initial: CronTaskDto?, skills: List<String>,
    onDismiss: () -> Unit, onSave: (String, String, String) -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editMode = initial != null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var cron by remember { mutableStateOf(initial?.cron ?: "0 9 * * *") }
    var skill by remember { mutableStateOf(initial?.skill ?: skills.firstOrNull() ?: "") }
    var menu by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = DuqColors.surfaceElevated) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (editMode) "Редактировать задачу" else "Новая задача",
                color = DuqColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            AutoField(name, { name = it }, "Название задачи")
            AutoField(cron, { cron = it }, "cron: мин час день месяц день-недели")
            Text(cronHuman(cron), color = DuqColors.textDim, fontSize = 12.sp)
            ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                OutlinedTextField(
                    value = skill, onValueChange = {}, readOnly = true,
                    label = { Text("Скилл") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = autoFieldColors()
                )
                ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (skills.isEmpty())
                        DropdownMenuItem(text = { Text("сначала создай скилл") }, onClick = { menu = false })
                    skills.forEach { s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = { skill = s; menu = false })
                    }
                }
            }
            Button(
                onClick = { if (name.isNotBlank() && cron.isNotBlank() && skill.isNotBlank()) onSave(name, cron, skill) },
                enabled = name.isNotBlank() && cron.isNotBlank() && skill.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DuqColors.primary, contentColor = DuqColors.background),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (editMode) "Сохранить" else "Создать задачу") }
        }
    }
}

/* ════════════════════ общие компоненты ════════════════════ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, color = DuqColors.textPrimary) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBackIosNew, "Назад", tint = DuqColors.textPrimary, modifier = Modifier.size(20.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DuqColors.background)
    )
}

@Composable
private fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = DuqColors.primary)
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String) =
    Column(
        Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = DuqColors.textDim, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = DuqColors.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(hint, color = DuqColors.textDim, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }

@Composable
private fun ErrorRow(msg: String) =
    Text("⚠️ $msg", color = DuqColors.accent, fontSize = 13.sp, modifier = Modifier.padding(8.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun autoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DuqColors.textPrimary, unfocusedTextColor = DuqColors.textPrimary,
    focusedBorderColor = DuqColors.primary, unfocusedBorderColor = DuqColors.textDim,
    focusedLabelColor = DuqColors.primary, unfocusedLabelColor = DuqColors.textDim,
    cursorColor = DuqColors.primary
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoField(value: String, onChange: (String) -> Unit, label: String, minLines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, minLines = minLines,
        modifier = Modifier.fillMaxWidth(), colors = autoFieldColors()
    )
}

/** "0 9 * * *" → "каждый день в 09:00"; иначе сырой cron. */
private fun cronHuman(cron: String?): String {
    if (cron.isNullOrBlank()) return "—"
    val p = cron.trim().split(Regex("\\s+"))
    if (p.size == 5) {
        val (m, h, dom, mon, dow) = p
        val mi = m.toIntOrNull(); val hh = h.toIntOrNull()
        if (mi != null && hh != null && dom == "*" && mon == "*") {
            val time = "%02d:%02d".format(hh, mi)
            return if (dow == "*") "каждый день в $time" else "по дням недели ($dow) в $time"
        }
    }
    return cron
}

/** "2026-06-23T01:00:00" → "23.06 01:00". */
private fun prettyNext(iso: String): String = runCatching {
    val d = iso.substringBefore("T"); val t = iso.substringAfter("T").take(5)
    val (y, mo, da) = d.split("-")
    "$da.$mo $t"
}.getOrDefault(iso)
