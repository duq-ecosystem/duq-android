package com.duq.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Корневой Composable DUQ — общий для Android и iOS (Compose Multiplatform).
 *
 * Каркас: пока выводит заглушку, чтобы проверить сборку KMP на обеих платформах.
 * Функционал (чат, голос, агенты, Пульт) переносится из референс-модуля `app/`
 * по слоям в commonMain/androidMain/iosMain.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("DUQ")
            }
        }
    }
}
