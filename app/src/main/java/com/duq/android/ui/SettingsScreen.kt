package com.duq.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.duq.android.data.SettingsRepository
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val isPaired by repo.isPaired.collectAsState(initial = false)
    var wakeWordSensitivity by remember { mutableFloatStateOf(repo.getWakeWordSensitivitySync()) }
    var silenceTimeout by remember { mutableFloatStateOf(repo.getSilenceTimeoutMsSync().toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gateway", style = MaterialTheme.typography.titleMedium)
                    Text(if (isPaired) "Paired with DUQ" else "Not paired",
                        color = if (isPaired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if (isPaired) {
                        OutlinedButton(onClick = { repo.clearPairing() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Unpair")
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Voice", style = MaterialTheme.typography.titleMedium)

            Text("Wake Word Sensitivity: ${(wakeWordSensitivity * 100).roundToInt()}%")
            Slider(value = wakeWordSensitivity, onValueChange = { wakeWordSensitivity = it },
                onValueChangeFinished = { repo.saveWakeWordSensitivity(wakeWordSensitivity) },
                valueRange = 0.5f..1.0f, modifier = Modifier.fillMaxWidth())

            Text("Silence Timeout: ${(silenceTimeout / 1000).roundToInt()}s")
            Slider(value = silenceTimeout, onValueChange = { silenceTimeout = it },
                onValueChangeFinished = { repo.saveSilenceTimeoutMs(silenceTimeout.toLong()) },
                valueRange = 1000f..4000f, modifier = Modifier.fillMaxWidth())
        }
    }
}
