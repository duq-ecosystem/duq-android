package com.duq.android.auth

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.util.Log
import com.duq.android.config.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token storage using Android AccountManager.
 *
 * CRITICAL: AccountManager data survives app data clear (pm clear),
 * unlike SharedPreferences or EncryptedSharedPreferences.
 *
 * This ensures biometric login works even after app data is cleared.
 */
@Singleton
class AccountTokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AccountTokenStorage"
    }

    private val accountManager: AccountManager by lazy {
        AccountManager.get(context)
    }

    /**
     * Get or create the Duq account.
     */
    private fun getOrCreateAccount(): Account {
        val accounts = accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE)

        return if (accounts.isNotEmpty()) {
            accounts[0]
        } else {
            val account = Account(
                DuqAccountAuthenticator.ACCOUNT_NAME,
                DuqAccountAuthenticator.ACCOUNT_TYPE
            )
            // Add account without password - tokens are stored separately
            accountManager.addAccountExplicitly(account, null, null)
            Log.d(TAG, "Created new Duq account")
            account
        }
    }

    /**
     * Get the existing Duq account, or null if not exists.
     */
    private fun getAccount(): Account? {
        val accounts = accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE)
        return accounts.firstOrNull()
    }

    // ========== TOKEN OPERATIONS ==========

    /**
     * Save access token.
     */
    fun saveAccessToken(token: String) {
        val account = getOrCreateAccount()
        accountManager.setAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_ACCESS, token)
        Log.d(TAG, "Access token saved")
    }

    /**
     * Get access token.
     */
    fun getAccessToken(): String {
        val account = getAccount() ?: return ""
        return accountManager.peekAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_ACCESS) ?: ""
    }

    /**
     * Save refresh token.
     */
    fun saveRefreshToken(token: String) {
        val account = getOrCreateAccount()
        accountManager.setAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_REFRESH, token)
        Log.d(TAG, "Refresh token saved")
    }

    /**
     * Get refresh token.
     */
    fun getRefreshToken(): String {
        val account = getAccount() ?: return ""
        return accountManager.peekAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_REFRESH) ?: ""
    }

    /**
     * Save ID token.
     */
    fun saveIdToken(token: String) {
        val account = getOrCreateAccount()
        accountManager.setAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_ID, token)
        Log.d(TAG, "ID token saved")
    }

    /**
     * Get ID token.
     */
    fun getIdToken(): String {
        val account = getAccount() ?: return ""
        return accountManager.peekAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_ID) ?: ""
    }

    /**
     * Save token expiration time.
     */
    fun saveExpiresAt(expiresAt: Long) {
        val account = getOrCreateAccount()
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_EXPIRES_AT, expiresAt.toString())
    }

    /**
     * Get token expiration time.
     */
    fun getExpiresAt(): Long {
        val account = getAccount() ?: return 0L
        return accountManager.getUserData(account, DuqAccountAuthenticator.KEY_EXPIRES_AT)?.toLongOrNull() ?: 0L
    }

    // ========== USER INFO OPERATIONS ==========

    /**
     * Save user info.
     */
    fun saveUserInfo(sub: String, email: String, name: String, username: String) {
        val account = getOrCreateAccount()
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_USER_SUB, sub)
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_USER_EMAIL, email)
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_USER_NAME, name)
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_USERNAME, username)
        Log.d(TAG, "User info saved: $username")
    }

    /**
     * Get user sub.
     */
    fun getUserSub(): String {
        val account = getAccount() ?: return ""
        return accountManager.getUserData(account, DuqAccountAuthenticator.KEY_USER_SUB) ?: ""
    }

    /**
     * Get user email.
     */
    fun getUserEmail(): String {
        val account = getAccount() ?: return ""
        return accountManager.getUserData(account, DuqAccountAuthenticator.KEY_USER_EMAIL) ?: ""
    }

    /**
     * Get user name.
     */
    fun getUserName(): String {
        val account = getAccount() ?: return ""
        return accountManager.getUserData(account, DuqAccountAuthenticator.KEY_USER_NAME) ?: ""
    }

    /**
     * Get username.
     */
    fun getUsername(): String {
        val account = getAccount() ?: return ""
        return accountManager.getUserData(account, DuqAccountAuthenticator.KEY_USERNAME) ?: ""
    }

    // ========== COMBINED OPERATIONS ==========

    /**
     * Save all auth tokens at once.
     */
    fun saveAuthTokens(
        accessToken: String,
        refreshToken: String?,
        idToken: String?,
        expiresAt: Long
    ) {
        val account = getOrCreateAccount()

        accountManager.setAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_ACCESS, accessToken)
        refreshToken?.let {
            accountManager.setAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_REFRESH, it)
        }
        idToken?.let {
            accountManager.setAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_ID, it)
        }
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_EXPIRES_AT, expiresAt.toString())

        Log.d(TAG, "All auth tokens saved")
    }

    /**
     * Update access token (after refresh).
     */
    fun updateAccessToken(accessToken: String, refreshToken: String?, expiresAt: Long) {
        val account = getOrCreateAccount()

        accountManager.setAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_ACCESS, accessToken)
        refreshToken?.let {
            accountManager.setAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_REFRESH, it)
        }
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_EXPIRES_AT, expiresAt.toString())

        Log.d(TAG, "Access token updated")
    }

    /**
     * Clear all tokens (logout).
     */
    fun clearTokens() {
        val account = getAccount() ?: return

        accountManager.invalidateAuthToken(
            DuqAccountAuthenticator.ACCOUNT_TYPE,
            accountManager.peekAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_ACCESS)
        )
        accountManager.invalidateAuthToken(
            DuqAccountAuthenticator.ACCOUNT_TYPE,
            accountManager.peekAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_REFRESH)
        )
        accountManager.invalidateAuthToken(
            DuqAccountAuthenticator.ACCOUNT_TYPE,
            accountManager.peekAuthToken(account, DuqAccountAuthenticator.TOKEN_TYPE_ID)
        )

        // Clear user data
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_EXPIRES_AT, null)
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_USER_SUB, null)
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_USER_EMAIL, null)
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_USER_NAME, null)
        accountManager.setUserData(account, DuqAccountAuthenticator.KEY_USERNAME, null)

        Log.d(TAG, "All tokens cleared")
    }

    /**
     * Check if user has stored refresh token (can use biometric login).
     */
    fun hasRefreshToken(): Boolean {
        return getRefreshToken().isNotBlank()
    }

    /**
     * Check if user is authenticated (has valid access token).
     */
    fun isAuthenticated(): Boolean {
        return getAccessToken().isNotBlank()
    }

    /**
     * Check if token is expired (with buffer for refresh).
     */
    fun isTokenExpired(): Boolean {
        val expiresAt = getExpiresAt()
        return System.currentTimeMillis() >= (expiresAt - AppConfig.TOKEN_EXPIRY_BUFFER_MS)
    }

    /**
     * Remove account completely (full cleanup).
     */
    fun removeAccount() {
        val account = getAccount() ?: return
        accountManager.removeAccountExplicitly(account)
        Log.d(TAG, "Account removed")
    }
}
