package com.duq.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.duq.android.auth.BiometricAuthManager
import com.duq.android.auth.KeycloakAuthManager
import com.duq.android.data.SettingsRepository
import com.duq.android.ui.DuqApp
import com.duq.android.ui.theme.DuqAndroidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

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

    @Inject
    lateinit var biometricAuthManager: BiometricAuthManager

    // Biometric login state
    private val _biometricLoginComplete = MutableStateFlow(false)
    val biometricLoginComplete = _biometricLoginComplete.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate called with intent: ${intent?.data}")

        // Handle Porcupine key via Intent (for adb setup)
        handleConfigIntent()

        // Check if this is an OAuth callback
        handleOAuthCallback(intent)

        // Try biometric login if possible
        tryBiometricLogin()

        enableEdgeToEdge()
        setContent {
            DuqAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DuqApp()
                }
            }
        }
    }

    /**
     * Try biometric login if:
     * 1. User was previously logged in (has refresh token)
     * 2. Biometric is available
     */
    private fun tryBiometricLogin() {
        lifecycleScope.launch {
            if (!biometricAuthManager.canUseBiometricLogin()) {
                Log.d(TAG, "Biometric login not available")
                _biometricLoginComplete.value = true
                return@launch
            }

            Log.d(TAG, "Attempting biometric login...")

            when (val result = biometricAuthManager.performBiometricLogin(this@MainActivity)) {
                is BiometricAuthManager.BiometricLoginResult.Success -> {
                    Log.d(TAG, "Biometric login successful!")
                    _biometricLoginComplete.value = true
                }
                is BiometricAuthManager.BiometricLoginResult.Canceled -> {
                    Log.d(TAG, "Biometric login canceled by user")
                    _biometricLoginComplete.value = true
                }
                is BiometricAuthManager.BiometricLoginResult.NeedsFullLogin -> {
                    Log.d(TAG, "Refresh token expired, need full Keycloak login")
                    _biometricLoginComplete.value = true
                }
                is BiometricAuthManager.BiometricLoginResult.Error -> {
                    Log.e(TAG, "Biometric login error: ${result.message}")
                    _biometricLoginComplete.value = true
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
        if (uri != null && uri.scheme == "com.duq.android" && uri.host == "oauth" && uri.path == "/callback") {
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
