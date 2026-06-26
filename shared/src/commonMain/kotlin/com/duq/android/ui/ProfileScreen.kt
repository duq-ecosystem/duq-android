package com.duq.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duq.android.data.SettingsRepository
import com.duq.android.network.duq.DuqRestClient
import com.duq.android.network.duq.IntegrationsResponse
import com.duq.android.ui.theme.DuqColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Профиль пользователя (мультиюзер). Best-practice место — отдельный экран с верхнего уровня
 * (аватар в топбаре), НЕ из Настроек. Шапка: аватар + имя + роль. Ниже — редактирование имени и
 * карточки интеграций (Obsidian-волт со своей E2EE-формой, Google). Личность по user_id, токен
 * общий на семью. См. Multi-User-Architecture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    rest: DuqRestClient = koinInject(),
    repo: SettingsRepository = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(repo.getUserName()) }
    val userId = remember { repo.getUserId() }
    var info by remember { mutableStateOf(IntegrationsResponse()) }
    var status by remember { mutableStateOf("") }

    suspend fun reload() { runCatching { info = rest.integrations() }.onSuccess {
        if (name.isBlank() && info.name.isNotBlank()) name = info.name
    } }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = DuqColors.background,
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ───────── Шапка: аватар + имя + роль ─────────
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape).background(DuqColors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.trim().firstOrNull()?.uppercase() ?: "?",
                        fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.Black,
                    )
                }
                Text(
                    name.ifBlank { "Без имени" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                RoleBadge(info.role)
                if (userId.isNotBlank()) {
                    Text("ID ${userId.take(8)}", style = MaterialTheme.typography.bodySmall,
                        color = DuqColors.textDim)
                }
            }

            // ───────── Имя ─────────
            SectionCard {
                Text("Имя", style = MaterialTheme.typography.titleSmall, color = DuqColors.textSecondary)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        repo.saveUserName(name.trim())
                        scope.launch {
                            status = runCatching {
                                if (userId.isBlank()) rest.ensureRegistered(name.trim().ifBlank { null })
                                else rest.updateProfile(name.trim())
                                reload(); "Сохранено"
                            }.getOrElse { "Ошибка: ${it.message}" }
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить") }
                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = DuqColors.textDim)
                }
            }

            // ───────── Интеграции ─────────
            Text("Интеграции", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp))

            ObsidianCard(connected = info.integrations.obsidian, onLinked = { scope.launch { reload() } },
                rest = rest)

            IntegrationCard(
                icon = Icons.Outlined.Mail,
                title = "Google",
                subtitle = "Почта и календарь",
                connected = info.integrations.google,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    val (label, color) = when (role) {
        "admin", "root" -> "Администратор" to DuqColors.primary
        "" -> "—" to DuqColors.textDim
        else -> "Пользователь" to DuqColors.textSecondary
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(DuqColors.surfaceVariant).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Карточка интеграции: иконка + название + статус-чип. */
@Composable
private fun IntegrationCard(icon: ImageVector, title: String, subtitle: String, connected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(DuqColors.surfaceVariant).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = title, tint = DuqColors.textSecondary,
            modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DuqColors.textDim)
        }
        StatusChip(connected)
    }
}

@Composable
private fun StatusChip(connected: Boolean) {
    if (connected) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.CheckCircle, null, tint = DuqColors.primary, modifier = Modifier.size(16.dp))
            Text("подключено", style = MaterialTheme.typography.labelMedium, color = DuqColors.primary)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.CloudOff, null, tint = DuqColors.textDim, modifier = Modifier.size(16.dp))
            Text("не подключено", style = MaterialTheme.typography.labelMedium, color = DuqColors.textDim)
        }
    }
}

/** Карточка Obsidian-волта: статус + раскрывающаяся форма привязки своего E2EE-волта. */
@Composable
private fun ObsidianCard(connected: Boolean, onLinked: () -> Unit, rest: DuqRestClient) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var salt by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(DuqColors.surfaceVariant).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.Folder, "Obsidian", tint = DuqColors.textSecondary,
                modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text("Obsidian-волт", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium)
                Text("Свой E2EE-волт через vault-sync", style = MaterialTheme.typography.bodySmall,
                    color = DuqColors.textDim)
            }
            StatusChip(connected)
        }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
            Text(if (expanded) "Скрыть" else if (connected) "Изменить" else "Подключить")
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("URL волта (vault-sync MCP)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text("MCP-токен (опц.)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("Passphrase (E2EE)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(salt, { salt = it }, label = { Text("Salt (base64)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        scope.launch {
                            err = runCatching {
                                rest.linkObsidian(url.trim(), pass, salt.trim(),
                                    token.trim().ifBlank { null })
                                expanded = false; onLinked(); ""
                            }.getOrElse { "Ошибка: ${it.message}" }
                        }
                    },
                    enabled = url.isNotBlank() && pass.isNotBlank() && salt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Привязать волт") }
                if (err.isNotBlank()) {
                    Text(err, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Start)
                }
            }
        }
    }
}
