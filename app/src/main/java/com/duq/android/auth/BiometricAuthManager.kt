package com.duq.android.auth

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.duq.android.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manages biometric authentication for quick re-login.
 *
 * Flow:
 * 1. After first successful Keycloak login, tokens are saved in EncryptedSharedPreferences
 * 2. On app restart, if refresh token exists, show biometric prompt
 * 3. After successful biometric auth, use refresh token to get new access token
 * 4. If biometric fails or refresh token expired, fall back to full Keycloak login
 */
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val tokenRefresher: TokenRefresher
) {
    companion object {
        private const val TAG = "BiometricAuthManager"

        // Allow both strong biometric (fingerprint, face) and device credential (PIN/pattern)
        private const val AUTHENTICATORS = BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }

    /**
     * Check if biometric authentication is available on this device.
     */
    fun isBiometricAvailable(): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)

        return when (biometricManager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                BiometricAvailability.Available

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricAvailability.NoHardware

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.HardwareUnavailable

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability.NotEnrolled

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.SecurityUpdateRequired

            else ->
                BiometricAvailability.Unknown
        }
    }

    /**
     * Check if we can use biometric quick login.
     * Returns true if:
     * 1. Biometric is available on device
     * 2. User has a stored refresh token (was previously logged in)
     */
    suspend fun canUseBiometricLogin(): Boolean {
        if (isBiometricAvailable() != BiometricAvailability.Available) {
            Log.d(TAG, "Biometric not available")
            return false
        }

        val refreshToken = settingsRepository.getRefreshToken()
        if (refreshToken.isBlank()) {
            Log.d(TAG, "No refresh token stored, cannot use biometric login")
            return false
        }

        Log.d(TAG, "Biometric login available")
        return true
    }

    /**
     * Show biometric prompt and authenticate.
     * Returns BiometricResult indicating success or failure.
     *
     * @param activity The FragmentActivity to show the prompt on
     * @param title Title text for the prompt
     * @param subtitle Subtitle text for the prompt
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Authenticate to continue",
        subtitle: String = "Use your fingerprint or face to login"
    ): BiometricResult = suspendCancellableCoroutine { continuation ->

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.d(TAG, "Biometric authentication succeeded")
                if (continuation.isActive) {
                    continuation.resume(BiometricResult.Success)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.e(TAG, "Biometric authentication error: $errorCode - $errString")
                if (continuation.isActive) {
                    val result = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON ->
                            BiometricResult.Canceled

                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            BiometricResult.Lockout(errString.toString())

                        else ->
                            BiometricResult.Error(errString.toString())
                    }
                    continuation.resume(result)
                }
            }

            override fun onAuthenticationFailed() {
                Log.w(TAG, "Biometric authentication failed (bad fingerprint/face)")
                // Don't resume here - user can try again
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        biometricPrompt.authenticate(promptInfo)

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }

    /**
     * Perform biometric login:
     * 1. Show biometric prompt
     * 2. If successful, refresh access token using stored refresh token
     * 3. Return result
     */
    suspend fun performBiometricLogin(activity: FragmentActivity): BiometricLoginResult {
        // First, authenticate with biometric
        when (val authResult = authenticate(activity)) {
            is BiometricResult.Success -> {
                Log.d(TAG, "Biometric auth successful, refreshing token...")
            }
            is BiometricResult.Canceled -> {
                return BiometricLoginResult.Canceled
            }
            is BiometricResult.Lockout -> {
                return BiometricLoginResult.Error(authResult.message)
            }
            is BiometricResult.Error -> {
                return BiometricLoginResult.Error(authResult.message)
            }
        }

        // Biometric succeeded, now refresh the token
        val refreshToken = settingsRepository.getRefreshToken()
        if (refreshToken.isBlank()) {
            Log.e(TAG, "No refresh token after biometric auth")
            return BiometricLoginResult.NeedsFullLogin
        }

        val refreshResult = tokenRefresher.refreshToken(refreshToken)

        return if (refreshResult.success) {
            // Save new tokens
            settingsRepository.updateAccessToken(
                accessToken = refreshResult.accessToken,
                refreshToken = refreshResult.refreshToken,
                expiresAt = refreshResult.expiresAt
            )
            Log.d(TAG, "Token refresh successful")
            BiometricLoginResult.Success
        } else {
            Log.e(TAG, "Token refresh failed: ${refreshResult.error}")
            // Refresh token might be expired, need full login
            BiometricLoginResult.NeedsFullLogin
        }
    }

    /**
     * Biometric hardware availability status.
     */
    sealed class BiometricAvailability {
        object Available : BiometricAvailability()
        object NoHardware : BiometricAvailability()
        object HardwareUnavailable : BiometricAvailability()
        object NotEnrolled : BiometricAvailability()
        object SecurityUpdateRequired : BiometricAvailability()
        object Unknown : BiometricAvailability()
    }

    /**
     * Result of biometric authentication attempt.
     */
    sealed class BiometricResult {
        object Success : BiometricResult()
        object Canceled : BiometricResult()
        data class Lockout(val message: String) : BiometricResult()
        data class Error(val message: String) : BiometricResult()
    }

    /**
     * Result of full biometric login flow (auth + token refresh).
     */
    sealed class BiometricLoginResult {
        object Success : BiometricLoginResult()
        object Canceled : BiometricLoginResult()
        object NeedsFullLogin : BiometricLoginResult()
        data class Error(val message: String) : BiometricLoginResult()
    }
}
