package com.jarvis.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.jarvis.android.auth.KeycloakAuthManager
import com.jarvis.android.data.SettingsRepository
import com.jarvis.android.ui.JarvisApp
import com.jarvis.android.ui.theme.JarvisAndroidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        // OAuth result flow for SettingsScreen to observe
        private val _oauthResultIntent = MutableStateFlow<Intent?>(null)
        val oauthResultIntent = _oauthResultIntent.asStateFlow()

        fun clearOAuthResult() {
            _oauthResultIntent.value = null
        }
    }

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var keycloakAuthManager: KeycloakAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate called with intent: ${intent?.data}")

        // Handle Porcupine key via Intent (for adb setup)
        handleConfigIntent()

        // Check if this is an OAuth callback
        handleOAuthCallback(intent)

        enableEdgeToEdge()
        setContent {
            JarvisAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JarvisApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called with intent: ${intent.data}")
        setIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun handleOAuthCallback(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "com.jarvis.android" && uri.host == "oauth" && uri.path == "/callback") {
            val code = uri.getQueryParameter("code")
            if (code == null) {
                Log.e(TAG, "OAuth callback missing code parameter")
                return
            }

            Log.d(TAG, "OAuth callback received with code: ${code.take(10)}...")

            // Exchange code for tokens manually
            lifecycleScope.launch {
                try {
                    val result = keycloakAuthManager.exchangeCodeForTokens(code)
                    result.fold(
                        onSuccess = { tokenResponse ->
                            Log.d(TAG, "OAuth success! Token: ${tokenResponse.accessToken.take(20)}...")
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
                                Log.d(TAG, "User info saved: ${userInfo.preferredUsername}")
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "OAuth token exchange error: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "OAuth exception", e)
                }
            }
        }
    }

    private fun handleConfigIntent() {
        // Only allow Porcupine key via intent (auth is handled by Keycloak)
        val porcupineKey = intent.getStringExtra("porcupine_key")

        if (porcupineKey != null) {
            lifecycleScope.launch {
                settingsRepository.savePorcupineApiKey(porcupineKey)
            }
        }
    }
}
