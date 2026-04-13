package com.jarvis.android.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvis.android.JarvisState
import com.jarvis.android.error.JarvisError
import com.jarvis.android.service.JarvisListenerService
import com.jarvis.android.ui.components.ArcReactor
import com.jarvis.android.ui.components.MessagesList

private val BackgroundBlack = Color(0xFF0D0D0D)
private val SurfaceGray = Color(0xFF1A1A1A)
private val IronManRed = Color(0xFFE62429)

@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var service by remember { mutableStateOf<JarvisListenerService?>(null) }

    val state by service?.state?.collectAsState() ?: remember { mutableStateOf(JarvisState.IDLE) }
    val serviceError by service?.error?.collectAsState() ?: remember { mutableStateOf<JarvisError?>(null) }
    val viewModelError by viewModel.error.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Combine errors - service error takes priority
    val currentError = serviceError ?: viewModelError

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? JarvisListenerService.LocalBinder)?.getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // Just bind, don't start as foreground service
            context.bindService(
                Intent(context, JarvisListenerService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    // Request permissions on launch
    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Track lifecycle to stop service when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBound by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (!isBound) {
                        context.bindService(
                            Intent(context, JarvisListenerService::class.java),
                            serviceConnection,
                            Context.BIND_AUTO_CREATE
                        )
                        isBound = true
                        // Refresh messages when app gains focus
                        viewModel.refreshMessages()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (isBound) {
                        try { context.unbindService(serviceConnection) } catch (_: Exception) {}
                        context.stopService(Intent(context, JarvisListenerService::class.java))
                        isBound = false
                        service = null
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (isBound) {
                try { context.unbindService(serviceConnection) } catch (_: Exception) {}
                context.stopService(Intent(context, JarvisListenerService::class.java))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Settings button - top right
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopEnd)
                .size(44.dp)
                .clip(CircleShape)
                .background(SurfaceGray)
                .clickable { onNavigateToSettings() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u2699",
                fontSize = 22.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        // Main content - Arc Reactor + Messages
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp) // Space for settings button
        ) {
            // Arc Reactor + Status - top section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ArcReactor(
                    state = state,
                    modifier = Modifier.size(180.dp) // Reduced from 240dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = getStatusText(state, currentError),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    color = if (currentError != null) IronManRed else Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp
                )
            }

            // Messages list - bottom section
            MessagesList(
                messages = messages,
                isLoading = isLoading,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun getStatusText(state: JarvisState, error: JarvisError?): String {
    // Show error message if in error state and error exists
    if (state == JarvisState.ERROR && error != null) {
        return error.toDisplayMessage().uppercase()
    }
    return when (state) {
        JarvisState.IDLE -> "SAY \"JARVIS\""
        JarvisState.LISTENING, JarvisState.RECORDING -> "LISTENING..."
        JarvisState.PROCESSING -> "PROCESSING..."
        JarvisState.PLAYING -> "SPEAKING..."
        JarvisState.ERROR -> "ERROR"
    }
}

