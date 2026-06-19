package com.duq.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.duq.android.ui.control.ActivityViewModel
import com.duq.android.ui.theme.DuqColors

/**
 * Лента — глобальный монитор на реальных RPC (spec §7): аппрувы (HITL),
 * cron-прогоны, задачи, расходы, хвост логов. Данные грузит [ActivityViewModel].
 */
@Composable
fun ActivityScreen(onOpenPalette: () -> Unit = {}, vm: ActivityViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp, start = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ЛЕНТА", fontSize = 13.sp, fontWeight = FontWeight.Light,
                color = DuqColors.textDim, letterSpacing = 3.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Search, contentDescription = "Поиск",
                    tint = DuqColors.textSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenPalette).padding(8.dp).size(22.dp))
                androidx.compose.material3.Icon(
                    Icons.Outlined.Refresh, contentDescription = "Обновить",
                    tint = DuqColors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.refresh() }.padding(8.dp).size(22.dp))
            }
        }

        if (s.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DuqColors.primary)
            }
            return
        }
        if (s.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("⚠️ ${s.error}", color = DuqColors.textMuted)
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            // Аппрувы (HITL) — верх, требуют решения.
            if (s.approvals.isNotEmpty()) {
                items(listOf(0)) { SectionHeader("⚠️ Аппрувы") }
                items(s.approvals) { a ->
                    Card {
                        Text(a.title, fontSize = 14.sp, color = DuqColors.textPrimary)
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionChip("Разрешить", DuqColors.success) { vm.resolveApproval(a, true) }
                            ActionChip("Отклонить", DuqColors.error) { vm.resolveApproval(a, false) }
                        }
                    }
                }
            }
            if (s.cronRuns.isNotEmpty()) {
                items(listOf(0)) { SectionHeader("⏰ Cron-прогоны") }
                items(s.cronRuns) { c -> RowCard(c.title, c.status) }
            }
            if (s.tasks.isNotEmpty()) {
                items(listOf(0)) { SectionHeader("📋 Задачи") }
                items(s.tasks) { t ->
                    Card {
                        Text(t.title, fontSize = 14.sp, color = DuqColors.textPrimary)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(t.status, fontSize = 12.sp, color = DuqColors.textSecondary)
                            ActionChip("Отменить", DuqColors.error) { vm.cancelTask(t) }
                        }
                    }
                }
            }
            if (s.costSummary.isNotBlank()) {
                items(listOf(0)) { SectionHeader("💰 Расходы") }
                items(listOf(0)) { RowCard(s.costSummary, "") }
            }
            if (s.logTail.isNotEmpty()) {
                items(listOf(0)) { SectionHeader("📜 Логи") }
                items(s.logTail) { line ->
                    Text(line, fontSize = 11.sp, color = DuqColors.textMuted, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            if (s.approvals.isEmpty() && s.cronRuns.isEmpty() && s.tasks.isEmpty() &&
                s.costSummary.isBlank() && s.logTail.isEmpty()) {
                items(listOf(0)) {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Text("Пока тихо — нет активности", color = DuqColors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DuqColors.textDim,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(DuqColors.surfaceVariant)
            .border(1.dp, DuqColors.glassBorder, RoundedCornerShape(12.dp)).padding(14.dp),
        content = content
    )
}

@Composable
private fun RowCard(title: String, subtitle: String) {
    Card {
        Text(title, fontSize = 14.sp, color = DuqColors.textPrimary)
        if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = DuqColors.textSecondary,
            modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ActionChip(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}
