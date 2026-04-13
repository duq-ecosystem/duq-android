package com.jarvis.android.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.android.R
import com.jarvis.android.auth.KeycloakAuthManager
import com.jarvis.android.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSettingsSaved: () -> Unit) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val keycloakAuthManager = remember { KeycloakAuthManager(context, settingsRepository) }
    val scope = rememberCoroutineScope()

    val savedAccessToken by settingsRepository.accessToken.collectAsState(initial = "")
    val savedUsername by settingsRepository.username.collectAsState(initial = "")
    val savedUserEmail by settingsRepository.userEmail.collectAsState(initial = "")
    val savedApiKey by settingsRepository.porcupineApiKey.collectAsState(initial = "")

    var porcupineApiKey by remember { mutableStateOf("") }
    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val isAuthenticated = savedAccessToken.isNotBlank()

    // OAuth result launcher
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // AppAuth may return with result code 0 (not RESULT_OK), but with valid data
        val data = result.data
        if (data != null) {
            scope.launch {
                isAuthenticating = true
                errorMessage = null

                keycloakAuthManager.handleAuthorizationResponse(
                    intent = data,
                    onSuccess = { accessToken, refreshToken, idToken, expiresAt ->
                        settingsRepository.saveAuthTokens(
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            idToken = idToken,
                            expiresAt = expiresAt
                        )

                        // Get user info
                        val userInfoResult = keycloakAuthManager.getUserInfo(accessToken)
                        userInfoResult.getOrNull()?.let { userInfo ->
                            settingsRepository.saveUserInfo(
                                sub = userInfo.sub,
                                email = userInfo.email,
                                name = userInfo.name,
                                username = userInfo.preferredUsername
                            )
                        }

                        successMessage = "Authenticated successfully!"
                        isAuthenticating = false
                    },
                    onError = { error ->
                        errorMessage = error
                        isAuthenticating = false
                    }
                )
            }
        } else {
            isAuthenticating = false
            if (result.resultCode == Activity.RESULT_CANCELED) {
                errorMessage = "Login cancelled"
            }
        }
    }

    LaunchedEffect(savedApiKey) {
        if (porcupineApiKey.isEmpty() && savedApiKey.isNotEmpty()) {
            porcupineApiKey = savedApiKey
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            keycloakAuthManager.dispose()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Auth Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAuthenticated)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isAuthenticated) stringResource(R.string.authenticated) else stringResource(R.string.not_authenticated),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isAuthenticated)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (isAuthenticated && savedUsername.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = savedUsername,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (savedUserEmail.isNotBlank()) {
                            Text(
                                text = savedUserEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!isAuthenticated) {
                // Keycloak Login Button
                Button(
                    onClick = {
                        isAuthenticating = true
                        errorMessage = null
                        successMessage = null

                        try {
                            val authIntent = keycloakAuthManager.getAuthorizationIntent()
                            authLauncher.launch(authIntent)
                        } catch (e: Exception) {
                            errorMessage = "Failed to start login: ${e.message}"
                            isAuthenticating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAuthenticating
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Authenticating...")
                    } else {
                        Text("Login with Keycloak")
                    }
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                successMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                // Logout button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val idToken = settingsRepository.getIdToken()
                            keycloakAuthManager.logout(idToken.ifBlank { null })
                            successMessage = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.logout))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Porcupine API Key
            Text(
                text = "Wake Word Settings",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = porcupineApiKey,
                onValueChange = { porcupineApiKey = it },
                label = { Text(stringResource(R.string.porcupine_api_key)) },
                placeholder = { Text(stringResource(R.string.porcupine_api_key_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Get free API key at console.picovoice.ai",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        settingsRepository.savePorcupineApiKey(porcupineApiKey)
                        onSettingsSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = porcupineApiKey.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
