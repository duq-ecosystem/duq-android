package com.duq.android.error

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DuqError sealed class
 */
class DuqErrorTest {

    @Test
    fun `NetworkError holds message and code`() {
        val error = DuqError.NetworkError("Connection failed", 404)

        assertEquals("Connection failed", error.message)
        assertEquals(404, error.code)
    }

    @Test
    fun `NetworkError code is optional`() {
        val error = DuqError.NetworkError("Timeout")

        assertEquals("Timeout", error.message)
        assertNull(error.code)
    }

    @Test
    fun `AudioError holds message`() {
        val error = DuqError.AudioError("Microphone not available")

        assertEquals("Microphone not available", error.message)
    }

    @Test
    fun `WakeWordError holds message`() {
        val error = DuqError.WakeWordError("Porcupine initialization failed")

        assertEquals("Porcupine initialization failed", error.message)
    }

    @Test
    fun `PermissionError formats message correctly`() {
        val error = DuqError.PermissionError("RECORD_AUDIO")

        assertEquals("Missing permission: RECORD_AUDIO", error.message)
        assertEquals("RECORD_AUDIO", error.permission)
    }

    @Test
    fun `ConfigurationError holds message`() {
        val error = DuqError.ConfigurationError("API key not set")

        assertEquals("API key not set", error.message)
    }

    @Test
    fun `AuthError holds message and requiresReauth flag`() {
        val error = DuqError.AuthError("Token expired", requiresReauth = true)

        assertEquals("Token expired", error.message)
        assertTrue(error.requiresReauth)
    }

    @Test
    fun `AuthError factory methods work correctly`() {
        val tokenExpired = DuqError.AuthError.tokenExpired()
        val invalidToken = DuqError.AuthError.invalidToken()
        val refreshFailed = DuqError.AuthError.refreshFailed("Network error")

        assertEquals("Session expired", tokenExpired.message)
        assertTrue(tokenExpired.requiresReauth)

        assertEquals("Invalid authentication", invalidToken.message)
        assertTrue(invalidToken.requiresReauth)

        assertEquals("Token refresh failed: Network error", refreshFailed.message)
        assertTrue(refreshFailed.requiresReauth)
    }

    @Test
    fun `NetworkError factory methods work correctly`() {
        val timeout = DuqError.NetworkError.timeout()
        val noConnection = DuqError.NetworkError.noConnection()
        val serverError = DuqError.NetworkError.serverError(500, "Internal error")
        val clientError = DuqError.NetworkError.clientError(404, "Not found")

        assertEquals("Request timed out", timeout.message)
        assertTrue(timeout.isRetryable)

        assertEquals("No internet connection", noConnection.message)
        assertTrue(noConnection.isRetryable)

        assertEquals("Internal error", serverError.message)
        assertEquals(500, serverError.code)
        assertTrue(serverError.isRetryable)

        assertEquals("Not found", clientError.message)
        assertEquals(404, clientError.code)
        assertFalse(clientError.isRetryable)
    }

    @Test
    fun `DuqError is sealed class with correct subclasses`() {
        val errors = listOf<DuqError>(
            DuqError.NetworkError("Network"),
            DuqError.AudioError("Audio"),
            DuqError.WakeWordError("WakeWord"),
            DuqError.PermissionError("PERMISSION"),
            DuqError.ConfigurationError("Config"),
            DuqError.AuthError("Auth")
        )

        assertEquals(6, errors.size)
        assertTrue(errors[0] is DuqError.NetworkError)
        assertTrue(errors[1] is DuqError.AudioError)
        assertTrue(errors[2] is DuqError.WakeWordError)
        assertTrue(errors[3] is DuqError.PermissionError)
        assertTrue(errors[4] is DuqError.ConfigurationError)
        assertTrue(errors[5] is DuqError.AuthError)
    }

    @Test
    fun `when expression covers all error types`() {
        val errors = listOf<DuqError>(
            DuqError.NetworkError("Network error"),
            DuqError.AudioError("Audio error"),
            DuqError.WakeWordError("Wake word error"),
            DuqError.PermissionError("PERMISSION"),
            DuqError.ConfigurationError("Config error"),
            DuqError.AuthError("Auth error")
        )

        for (error in errors) {
            val result = when (error) {
                is DuqError.NetworkError -> "network"
                is DuqError.AudioError -> "audio"
                is DuqError.WakeWordError -> "wakeword"
                is DuqError.PermissionError -> "permission"
                is DuqError.ConfigurationError -> "config"
                is DuqError.AuthError -> "auth"
            }
            assertTrue(result.isNotEmpty())
        }
    }

    @Test
    fun `toDisplayMessage returns user-friendly messages`() {
        assertEquals("Connection timed out. Please try again.", DuqError.NetworkError.timeout().toDisplayMessage())
        assertEquals("Server error. Please try again.", DuqError.NetworkError.serverError(500, "Error").toDisplayMessage())
        assertEquals("Authentication error. Please re-login.", DuqError.NetworkError.clientError(401, "Unauthorized").toDisplayMessage())
        assertEquals("Not found.", DuqError.NetworkError.clientError(404, "Not found").toDisplayMessage())
        assertEquals("Network error. Check your connection.", DuqError.NetworkError("Some error").toDisplayMessage())

        assertEquals("Audio error: Recording failed", DuqError.AudioError.recordingFailed().toDisplayMessage())
        assertEquals("Voice activation error: Wake word initialization failed", DuqError.WakeWordError.initFailed().toDisplayMessage())
        assertEquals("Permission required: android.permission.RECORD_AUDIO", DuqError.PermissionError.microphone().toDisplayMessage())
        assertEquals("Setup error: API key not configured", DuqError.ConfigurationError.missingApiKey("API key").toDisplayMessage())
        assertEquals("Please log in again", DuqError.AuthError.tokenExpired().toDisplayMessage())
    }
}
