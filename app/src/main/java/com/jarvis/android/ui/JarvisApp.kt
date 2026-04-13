package com.jarvis.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jarvis.android.data.SettingsRepository

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Settings : Screen("settings")
}

@Composable
fun JarvisApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }

    val hasValidSettings by settingsRepository.hasValidSettings.collectAsState(initial = null)

    val startDestination = when (hasValidSettings) {
        true -> Screen.Main.route
        false -> Screen.Settings.route
        null -> Screen.Settings.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onSettingsSaved = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Settings.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
