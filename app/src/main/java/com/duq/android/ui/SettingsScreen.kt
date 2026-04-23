package com.duq.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.duq.android.R
import com.duq.android.auth.KeycloakAuthManager
import com.duq.android.data.SettingsRepository
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

    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Direct login fields
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    val isAuthenticated = savedAccessToken.isNotBlank()

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
                // Username field
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isAuthenticating,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isAuthenticating,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (usernameInput.isNotBlank() && passwordInput.isNotBlank()) {
                                scope.launch {
                                    isAuthenticating = true
                                    errorMessage = null
                                    successMessage = null

                                    val result = keycloakAuthManager.loginWithPassword(
                                        username = usernameInput.trim(),
                                        password = passwordInput
                                    )

                                    result.fold(
                                        onSuccess = { tokenResponse ->
                                            settingsRepository.saveAuthTokens(
                                                accessToken = tokenResponse.accessToken,
                                                refreshToken = tokenResponse.refreshToken,
                                                idToken = tokenResponse.idToken,
                                                expiresAt = tokenResponse.expiresAt
                                            )

                                            // Get user info
                                            val userInfoResult = keycloakAuthManager.getUserInfo(tokenResponse.accessToken)
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
                                            passwordInput = ""
                                            onSettingsSaved()
                                        },
                                        onFailure = { error ->
                                            errorMessage = error.message ?: "Login failed"
                                            isAuthenticating = false
                                        }
                                    )
                                }
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Login Button
                Button(
                    onClick = {
                        scope.launch {
                            isAuthenticating = true
                            errorMessage = null
                            successMessage = null

                            val result = keycloakAuthManager.loginWithPassword(
                                username = usernameInput.trim(),
                                password = passwordInput
                            )

                            result.fold(
                                onSuccess = { tokenResponse ->
                                    settingsRepository.saveAuthTokens(
                                        accessToken = tokenResponse.accessToken,
                                        refreshToken = tokenResponse.refreshToken,
                                        idToken = tokenResponse.idToken,
                                        expiresAt = tokenResponse.expiresAt
                                    )

                                    // Get user info
                                    val userInfoResult = keycloakAuthManager.getUserInfo(tokenResponse.accessToken)
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
                                    passwordInput = ""
                                    onSettingsSaved()
                                },
                                onFailure = { error ->
                                    errorMessage = error.message ?: "Login failed"
                                    isAuthenticating = false
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAuthenticating && usernameInput.isNotBlank() && passwordInput.isNotBlank()
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logging in...")
                    } else {
                        Text("Login")
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

        }
    }
}
