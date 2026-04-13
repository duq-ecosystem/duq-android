package com.duq.android.network

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Unit tests for DuqApiClient
 * Tests retry logic and error classification
 */
class DuqApiClientTest {

    private val client = DuqApiClient()

    // ==================== isRetryableException Tests ====================

    @Test
    fun `SocketTimeoutException is retryable`() {
        val exception = SocketTimeoutException("Connection timed out")
        assertTrue(isRetryable(exception))
    }

    @Test
    fun `UnknownHostException is retryable`() {
        val exception = UnknownHostException("Unable to resolve host")
        assertTrue(isRetryable(exception))
    }

    @Test
    fun `ConnectException is retryable`() {
        val exception = java.net.ConnectException("Connection refused")
        assertTrue(isRetryable(exception))
    }

    @Test
    fun `SSLException with connection reset is retryable`() {
        val exception = SSLException("Connection reset by peer")
        assertTrue(isRetryable(exception))
    }

    @Test
    fun `SSLException without connection reset is not retryable`() {
        val exception = SSLException("Certificate validation failed")
        assertFalse(isRetryable(exception))
    }

    @Test
    fun `IOException with timeout message is retryable`() {
        val exception = IOException("Read timeout")
        assertTrue(isRetryable(exception))
    }

    @Test
    fun `IOException with connection message is retryable`() {
        val exception = IOException("Connection failed")
        assertTrue(isRetryable(exception))
    }

    @Test
    fun `IOException with reset message is retryable`() {
        val exception = IOException("Connection reset")
        assertTrue(isRetryable(exception))
    }

    @Test
    fun `IOException with other message is not retryable`() {
        val exception = IOException("File not found")
        assertFalse(isRetryable(exception))
    }

    @Test
    fun `IllegalArgumentException is not retryable`() {
        val exception = IllegalArgumentException("Invalid argument")
        assertFalse(isRetryable(exception))
    }

    @Test
    fun `NullPointerException is not retryable`() {
        val exception = NullPointerException("Null reference")
        assertFalse(isRetryable(exception))
    }

    @Test
    fun `SecurityException is not retryable`() {
        val exception = SecurityException("Permission denied")
        assertFalse(isRetryable(exception))
    }

    // ==================== ApiResult Tests ====================

    @Test
    fun `ApiResult Success holds audio data and text`() {
        val audioData = byteArrayOf(1, 2, 3, 4)
        val text = "Hello"
        val result = DuqApiClient.ApiResult.Success(audioData, text)

        assertArrayEquals(audioData, result.audioData)
        assertEquals(text, result.text)
    }

    @Test
    fun `ApiResult Success with default empty text`() {
        val audioData = byteArrayOf(1, 2, 3)
        val result = DuqApiClient.ApiResult.Success(audioData)

        assertArrayEquals(audioData, result.audioData)
        assertEquals("", result.text)
    }

    @Test
    fun `ApiResult Success equality works correctly`() {
        val result1 = DuqApiClient.ApiResult.Success(byteArrayOf(1, 2, 3), "test")
        val result2 = DuqApiClient.ApiResult.Success(byteArrayOf(1, 2, 3), "test")
        val result3 = DuqApiClient.ApiResult.Success(byteArrayOf(1, 2, 4), "test")

        assertEquals(result1, result2)
        assertNotEquals(result1, result3)
    }

    @Test
    fun `ApiResult Error holds message and code`() {
        val result = DuqApiClient.ApiResult.Error("Not found", 404)

        assertEquals("Not found", result.message)
        assertEquals(404, result.code)
    }

    @Test
    fun `ApiResult Error code is optional`() {
        val result = DuqApiClient.ApiResult.Error("Generic error")

        assertEquals("Generic error", result.message)
        assertNull(result.code)
    }

    // ==================== TokenRefreshResult Tests ====================

    @Test
    fun `TokenRefreshResult successful has correct fields`() {
        val result = DuqApiClient.TokenRefreshResult(
            success = true,
            accessToken = "new-token",
            refreshToken = "new-refresh",
            expiresAt = 1000L
        )

        assertTrue(result.success)
        assertEquals("new-token", result.accessToken)
        assertEquals("new-refresh", result.refreshToken)
        assertEquals(1000L, result.expiresAt)
        assertEquals("", result.error)
    }

    @Test
    fun `TokenRefreshResult failure has error message`() {
        val result = DuqApiClient.TokenRefreshResult(
            success = false,
            error = "Token expired"
        )

        assertFalse(result.success)
        assertEquals("Token expired", result.error)
        assertEquals("", result.accessToken)
    }

    @Test
    fun `TokenRefreshResult default values`() {
        val result = DuqApiClient.TokenRefreshResult(success = true)

        assertTrue(result.success)
        assertEquals("", result.accessToken)
        assertNull(result.refreshToken)
        assertEquals(0L, result.expiresAt)
        assertEquals("", result.error)
    }

    // ==================== BASE_URL Tests ====================

    @Test
    fun `BASE_URL is not empty`() {
        assertTrue(DuqApiClient.BASE_URL.isNotEmpty())
    }

    @Test
    fun `BASE_URL starts with https`() {
        assertTrue(DuqApiClient.BASE_URL.startsWith("https://"))
    }

    // ==================== Helper ====================

    /**
     * Use reflection to access private isRetryableException method
     */
    private fun isRetryable(e: Exception): Boolean {
        val method = DuqApiClient::class.java.getDeclaredMethod("isRetryableException", Exception::class.java)
        method.isAccessible = true
        return method.invoke(client, e) as Boolean
    }
}
