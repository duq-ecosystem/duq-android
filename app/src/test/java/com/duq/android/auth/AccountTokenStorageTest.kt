package com.duq.android.auth

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AccountTokenStorage.
 * Tests token storage operations using AccountManager.
 */
class AccountTokenStorageTest {

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var accountManager: AccountManager

    private lateinit var accountTokenStorage: AccountTokenStorage

    private val testAccount = Account(
        DuqAccountAuthenticator.ACCOUNT_NAME,
        DuqAccountAuthenticator.ACCOUNT_TYPE
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        mockkStatic(AccountManager::class)
        every { AccountManager.get(context) } returns accountManager
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(AccountManager::class)
    }

    @Test
    fun `getAccessToken returns empty string when no account exists`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns emptyArray()

        accountTokenStorage = AccountTokenStorage(context)
        val token = accountTokenStorage.getAccessToken()

        assertEquals("", token)
    }

    @Test
    fun `getAccessToken returns token when account exists`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        every { accountManager.peekAuthToken(testAccount, DuqAccountAuthenticator.TOKEN_TYPE_ACCESS) } returns "test-access-token"

        accountTokenStorage = AccountTokenStorage(context)
        val token = accountTokenStorage.getAccessToken()

        assertEquals("test-access-token", token)
    }

    @Test
    fun `getRefreshToken returns token when account exists`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        every { accountManager.peekAuthToken(testAccount, DuqAccountAuthenticator.TOKEN_TYPE_REFRESH) } returns "test-refresh-token"

        accountTokenStorage = AccountTokenStorage(context)
        val token = accountTokenStorage.getRefreshToken()

        assertEquals("test-refresh-token", token)
    }

    @Test
    fun `hasRefreshToken returns true when refresh token exists`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        every { accountManager.peekAuthToken(testAccount, DuqAccountAuthenticator.TOKEN_TYPE_REFRESH) } returns "test-refresh-token"

        accountTokenStorage = AccountTokenStorage(context)

        assertTrue(accountTokenStorage.hasRefreshToken())
    }

    @Test
    fun `hasRefreshToken returns false when no refresh token`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        every { accountManager.peekAuthToken(testAccount, DuqAccountAuthenticator.TOKEN_TYPE_REFRESH) } returns null

        accountTokenStorage = AccountTokenStorage(context)

        assertFalse(accountTokenStorage.hasRefreshToken())
    }

    @Test
    fun `isAuthenticated returns true when access token exists`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        every { accountManager.peekAuthToken(testAccount, DuqAccountAuthenticator.TOKEN_TYPE_ACCESS) } returns "test-token"

        accountTokenStorage = AccountTokenStorage(context)

        assertTrue(accountTokenStorage.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns false when no access token`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        every { accountManager.peekAuthToken(testAccount, DuqAccountAuthenticator.TOKEN_TYPE_ACCESS) } returns ""

        accountTokenStorage = AccountTokenStorage(context)

        assertFalse(accountTokenStorage.isAuthenticated())
    }

    @Test
    fun `saveAccessToken creates account if not exists`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns emptyArray() andThen arrayOf(testAccount)
        every { accountManager.addAccountExplicitly(any(), any(), any()) } returns true

        accountTokenStorage = AccountTokenStorage(context)
        accountTokenStorage.saveAccessToken("new-token")

        verify { accountManager.addAccountExplicitly(any(), null, null) }
        verify { accountManager.setAuthToken(any(), DuqAccountAuthenticator.TOKEN_TYPE_ACCESS, "new-token") }
    }

    @Test
    fun `saveAuthTokens saves all tokens`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)

        accountTokenStorage = AccountTokenStorage(context)
        accountTokenStorage.saveAuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            idToken = "id",
            expiresAt = 1234567890L
        )

        verify { accountManager.setAuthToken(testAccount, DuqAccountAuthenticator.TOKEN_TYPE_ACCESS, "access") }
        verify { accountManager.setAuthToken(testAccount, DuqAccountAuthenticator.TOKEN_TYPE_REFRESH, "refresh") }
        verify { accountManager.setAuthToken(testAccount, DuqAccountAuthenticator.TOKEN_TYPE_ID, "id") }
        verify { accountManager.setUserData(testAccount, DuqAccountAuthenticator.KEY_EXPIRES_AT, "1234567890") }
    }

    @Test
    fun `getExpiresAt returns stored expiration time`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        every { accountManager.getUserData(testAccount, DuqAccountAuthenticator.KEY_EXPIRES_AT) } returns "1234567890"

        accountTokenStorage = AccountTokenStorage(context)
        val expiresAt = accountTokenStorage.getExpiresAt()

        assertEquals(1234567890L, expiresAt)
    }

    @Test
    fun `isTokenExpired returns true when token is expired`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        // Set expiration to a past time
        every { accountManager.getUserData(testAccount, DuqAccountAuthenticator.KEY_EXPIRES_AT) } returns "1000"

        accountTokenStorage = AccountTokenStorage(context)

        assertTrue(accountTokenStorage.isTokenExpired())
    }

    @Test
    fun `isTokenExpired returns false when token is not expired`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        // Set expiration to a future time (current time + 1 hour)
        val futureTime = System.currentTimeMillis() + 3600000
        every { accountManager.getUserData(testAccount, DuqAccountAuthenticator.KEY_EXPIRES_AT) } returns futureTime.toString()

        accountTokenStorage = AccountTokenStorage(context)

        assertFalse(accountTokenStorage.isTokenExpired())
    }

    @Test
    fun `saveUserInfo stores user data`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)

        accountTokenStorage = AccountTokenStorage(context)
        accountTokenStorage.saveUserInfo(
            sub = "user-sub-123",
            email = "test@example.com",
            name = "Test User",
            username = "testuser"
        )

        verify { accountManager.setUserData(testAccount, DuqAccountAuthenticator.KEY_USER_SUB, "user-sub-123") }
        verify { accountManager.setUserData(testAccount, DuqAccountAuthenticator.KEY_USER_EMAIL, "test@example.com") }
        verify { accountManager.setUserData(testAccount, DuqAccountAuthenticator.KEY_USER_NAME, "Test User") }
        verify { accountManager.setUserData(testAccount, DuqAccountAuthenticator.KEY_USERNAME, "testuser") }
    }

    @Test
    fun `getUserSub returns stored sub`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        every { accountManager.getUserData(testAccount, DuqAccountAuthenticator.KEY_USER_SUB) } returns "user-sub-123"

        accountTokenStorage = AccountTokenStorage(context)
        val sub = accountTokenStorage.getUserSub()

        assertEquals("user-sub-123", sub)
    }

    @Test
    fun `clearTokens invalidates all tokens`() {
        every { accountManager.getAccountsByType(DuqAccountAuthenticator.ACCOUNT_TYPE) } returns arrayOf(testAccount)
        every { accountManager.peekAuthToken(testAccount, any()) } returns "some-token"

        accountTokenStorage = AccountTokenStorage(context)
        accountTokenStorage.clearTokens()

        verify(exactly = 3) { accountManager.invalidateAuthToken(DuqAccountAuthenticator.ACCOUNT_TYPE, "some-token") }
        verify { accountManager.setUserData(testAccount, DuqAccountAuthenticator.KEY_EXPIRES_AT, null) }
        verify { accountManager.setUserData(testAccount, DuqAccountAuthenticator.KEY_USER_SUB, null) }
        verify { accountManager.setUserData(testAccount, DuqAccountAuthenticator.KEY_USER_EMAIL, null) }
        verify { accountManager.setUserData(testAccount, DuqAccountAuthenticator.KEY_USER_NAME, null) }
        verify { accountManager.setUserData(testAccount, DuqAccountAuthenticator.KEY_USERNAME, null) }
    }
}
