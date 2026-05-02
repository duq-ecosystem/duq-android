package com.duq.android.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.duq.android.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.duq.android.DuqState
import com.duq.android.audio.ChatAudioPlaybackManager
import com.duq.android.audio.PlaybackInfo
import com.duq.android.config.AppConfig
import com.duq.android.error.DuqError
import com.duq.android.service.DuqListenerService
import com.duq.android.service.VoiceServiceController
import com.duq.android.ui.components.DuqDuck
import com.duq.android.ui.components.MessagesList
import com.duq.android.ui.theme.DuqColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel(),
    audioPlaybackManager: ChatAudioPlaybackManager
) {
    val context = LocalContext.current
    var voiceController by remember { mutableStateOf<VoiceServiceController?>(null) }

    val voiceState by voiceController?.state?.collectAsState() ?: remember { mutableStateOf(DuqState.IDLE) }
    val serviceError by voiceController?.error?.collectAsState() ?: remember { mutableStateOf<DuqError?>(null) }
    val viewModelError by viewModel.error.collectAsState()
    val isTextProcessing by viewModel.isProcessing.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConversation by viewModel.currentConversation.collectAsState()

    // Combine voice state with text processing state
    val state = when {
        isTextProcessing -> DuqState.PROCESSING
        else -> voiceState
    }

    // Audio playback state
    val audioPlaybackInfo by audioPlaybackManager.playbackInfo.collectAsState()

    // Text input state
    var textInput by remember { mutableStateOf("") }

    // Bottom sheet state for conversation picker
    var showConversationPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Snackbar for error display
    val snackbarHostState = remember { SnackbarHostState() }

    // Combine errors - service error takes priority
    val currentError = serviceError ?: viewModelError

    // Show snackbar when error occurs
    LaunchedEffect(currentError) {
        currentError?.let { error ->
            snackbarHostState.showSnackbar(
                message = error.toDisplayMessage(),
                withDismissAction = true
            )
            // Clear both ViewModel and service errors to prevent re-showing
            viewModel.clearError()
            voiceController?.clearError()
        }
    }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                voiceController = (binder as? DuqListenerService.LocalBinder)?.getController()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                voiceController = null
            }
        }
    }

    // Track lifecycle state
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBound by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Just mark permissions as granted - binding happens in lifecycle observer
        permissionsGranted = permissions.values.all { it }
    }

    // Request permissions on launch
    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Track service binding start time for timeout
    var bindingStartTime by remember { mutableStateOf(0L) }

    // Start foreground service and bind when permissions are granted
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted && !isBound) {
            bindingStartTime = System.currentTimeMillis()
            // Start as foreground service for background operation
            val serviceIntent = Intent(context, DuqListenerService::class.java).apply {
                action = DuqListenerService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            // Also bind to get service reference for UI updates
            context.bindService(
                Intent(context, DuqListenerService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            isBound = true
        }
    }

    // Service binding timeout warning
    LaunchedEffect(isBound, voiceController) {
        if (isBound && voiceController == null && bindingStartTime > 0) {
            delay(AppConfig.SERVICE_BIND_TIMEOUT_MS)
            if (voiceController == null) {
                Log.w("MainScreen", "Service binding timeout after ${AppConfig.SERVICE_BIND_TIMEOUT_MS}ms")
            }
        }
    }

    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // Rebind to get service reference for UI updates
                    if (permissionsGranted && !isBound) {
                        context.bindService(
                            Intent(context, DuqListenerService::class.java),
                            serviceConnection,
                            Context.BIND_AUTO_CREATE
                        )
                        isBound = true
                    }
                    // Only refresh messages, don't reload entire UI
                    // Full load happens in ViewModel init
                    viewModel.refreshMessages()
                }
                Lifecycle.Event.ON_STOP -> {
                    // Only unbind, don't stop service - it should keep running for WebSocket
                    if (isBound) {
                        try { context.unbindService(serviceConnection) } catch (_: Exception) {}
                        isBound = false
                        voiceController = null
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
                // Don't stop service - let it manage its own lifecycle
            }
        }
    }

    // Conversation picker bottom sheet
    if (showConversationPicker) {
        ModalBottomSheet(
            onDismissRequest = { showConversationPicker = false },
            sheetState = sheetState,
            containerColor = DuqColors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Conversations",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuqColors.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (conversations.isEmpty()) {
                    Text(
                        text = "No conversations yet",
                        fontSize = 14.sp,
                        color = DuqColors.textSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn {
                        items(conversations) { conversation ->
                            val isSelected = conversation.id == currentConversation?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) DuqColors.primary.copy(alpha = 0.1f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        viewModel.loadMessagesForConversation(conversation.id)
                                        showConversationPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = conversation.title ?: conversation.id,
                                        fontSize = 16.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = DuqColors.textPrimary
                                    )
                                    if (conversation.isActive) {
                                        Text(
                                            text = "Active",
                                            fontSize = 12.sp,
                                            color = DuqColors.primary
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        fontSize = 18.sp,
                                        color = DuqColors.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuqColors.background)
    ) {
        // Header row with conversation title and settings
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Conversation title (clickable to open picker)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showConversationPicker = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentConversation?.title ?: "Today",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = DuqColors.textPrimary
                )
                Text(
                    text = " ▼",
                    fontSize = 12.sp,
                    color = DuqColors.textSecondary
                )
            }

            // Settings button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DuqColors.surfaceVariant)
                    .clickable { onNavigateToSettings() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u2699",
                    fontSize = 22.sp,
                    color = DuqColors.textSecondary
                )
            }
        }

        // Main content - Arc Reactor + Messages + Text Input
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp) // Space for header
        ) {
            // Arc Reactor + Status - top section (smaller)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DuqDuck(
                    state = state,
                    modifier = Modifier.size(140.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = getStatusText(state, currentError),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    color = if (currentError != null) DuqColors.error else DuqColors.textDim,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp
                )
            }

            // Messages list - middle section (no loading spinner, uses optimistic updates)
            MessagesList(
                messages = messages,
                audioPlaybackInfo = audioPlaybackInfo,
                onAudioPlayPauseClick = { messageId ->
                    audioPlaybackManager.playOrToggle(messageId)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            // Text input - bottom section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "Type a message...",
                            color = DuqColors.textMuted
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DuqColors.textPrimary,
                        unfocusedTextColor = DuqColors.textPrimary,
                        focusedBorderColor = DuqColors.primary,
                        unfocusedBorderColor = DuqColors.surfaceElevated,
                        focusedContainerColor = DuqColors.surface,
                        unfocusedContainerColor = DuqColors.surface,
                        cursorColor = DuqColors.primary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            Log.d("MainScreen", "⌨️ Keyboard Send pressed, text='${textInput.take(20)}'")
                            if (textInput.isNotBlank()) {
                                viewModel.sendTextMessage(textInput)
                                textInput = ""
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (textInput.isNotBlank()) DuqColors.primary
                            else DuqColors.surfaceElevated
                        )
                        .clickable(enabled = textInput.isNotBlank()) {
                            Log.d("MainScreen", "🔘 Send button clicked, text='${textInput.take(20)}'")
                            viewModel.sendTextMessage(textInput)
                            textInput = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "➤",
                        fontSize = 20.sp,
                        color = if (textInput.isNotBlank()) Color.Black else DuqColors.textMuted
                    )
                }
            }
        }

        // Snackbar for error messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = DuqColors.error,
                contentColor = Color.White
            )
        }
    }
}

@Composable
private fun getStatusText(state: DuqState, error: DuqError?): String {
    // Show error message if in error state and error exists
    if (state == DuqState.ERROR && error != null) {
        return error.toDisplayMessage().uppercase()
    }
    return when (state) {
        DuqState.IDLE -> stringResource(R.string.status_idle)
        DuqState.LISTENING, DuqState.RECORDING -> stringResource(R.string.status_listening)
        DuqState.PROCESSING -> stringResource(R.string.status_processing)
        DuqState.PLAYING -> stringResource(R.string.status_playing)
        DuqState.ERROR -> stringResource(R.string.status_error)
    }
}

