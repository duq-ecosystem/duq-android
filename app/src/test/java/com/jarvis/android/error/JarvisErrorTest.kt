package com.jarvis.android.error

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for JarvisError sealed class
 */
class JarvisErrorTest {

    @Test
    fun `NetworkError holds message and code`() {
        val error = JarvisError.NetworkError("Connection failed", 404)

        assertEquals("Connection failed", error.message)
        assertEquals(404, error.code)
    }

    @Test
    fun `NetworkError code is optional`() {
        val error = JarvisError.NetworkError("Timeout")

        assertEquals("Timeout", error.message)
        assertNull(error.code)
    }

    @Test
    fun `AudioError holds message`() {
        val error = JarvisError.AudioError("Microphone not available")

        assertEquals("Microphone not available", error.message)
    }

    @Test
    fun `WakeWordError holds message`() {
        val error = JarvisError.WakeWordError("Porcupine initialization failed")

        assertEquals("Porcupine initialization failed", error.message)
    }

    @Test
    fun `PermissionError formats message correctly`() {
        val error = JarvisError.PermissionError("RECORD_AUDIO")

        assertEquals("Missing permission: RECORD_AUDIO", error.message)
        assertEquals("RECORD_AUDIO", error.permission)
    }

    @Test
    fun `ConfigurationError holds message`() {
        val error = JarvisError.ConfigurationError("API key not set")

        assertEquals("API key not set", error.message)
    }

    @Test
    fun `AuthError holds message and requiresReauth flag`() {
        val error = JarvisError.AuthError("Token expired", requiresReauth = true)

        assertEquals("Token expired", error.message)
        assertTrue(error.requiresReauth)
    }

    @Test
    fun `AuthError factory methods work correctly`() {
        val tokenExpired = JarvisError.AuthError.tokenExpired()
        val invalidToken = JarvisError.AuthError.invalidToken()
        val refreshFailed = JarvisError.AuthError.refreshFailed("Network error")

        assertEquals("Session expired", tokenExpired.message)
        assertTrue(tokenExpired.requiresReauth)

        assertEquals("Invalid authentication", invalidToken.message)
        assertTrue(invalidToken.requiresReauth)

        assertEquals("Token refresh failed: Network error", refreshFailed.message)
        assertTrue(refreshFailed.requiresReauth)
    }

    @Test
    fun `NetworkError factory methods work correctly`() {
        val timeout = JarvisError.NetworkError.timeout()
        val noConnection = JarvisError.NetworkError.noConnection()
        val serverError = JarvisError.NetworkError.serverError(500, "Internal error")
        val clientError = JarvisError.NetworkError.clientError(404, "Not found")

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
    fun `JarvisError is sealed class with correct subclasses`() {
        val errors = listOf<JarvisError>(
            JarvisError.NetworkError("Network"),
            JarvisError.AudioError("Audio"),
            JarvisError.WakeWordError("WakeWord"),
            JarvisError.PermissionError("PERMISSION"),
            JarvisError.ConfigurationError("Config"),
            JarvisError.AuthError("Auth")
        )

        assertEquals(6, errors.size)
        assertTrue(errors[0] is JarvisError.NetworkError)
        assertTrue(errors[1] is JarvisError.AudioError)
        assertTrue(errors[2] is JarvisError.WakeWordError)
        assertTrue(errors[3] is JarvisError.PermissionError)
        assertTrue(errors[4] is JarvisError.ConfigurationError)
        assertTrue(errors[5] is JarvisError.AuthError)
    }

    @Test
    fun `when expression covers all error types`() {
        val errors = listOf<JarvisError>(
            JarvisError.NetworkError("Network error"),
            JarvisError.AudioError("Audio error"),
            JarvisError.WakeWordError("Wake word error"),
            JarvisError.PermissionError("PERMISSION"),
            JarvisError.ConfigurationError("Config error"),
            JarvisError.AuthError("Auth error")
        )

        for (error in errors) {
            val result = when (error) {
                is JarvisError.NetworkError -> "network"
                is JarvisError.AudioError -> "audio"
                is JarvisError.WakeWordError -> "wakeword"
                is JarvisError.PermissionError -> "permission"
                is JarvisError.ConfigurationError -> "config"
                is JarvisError.AuthError -> "auth"
            }
            assertTrue(result.isNotEmpty())
        }
    }

    @Test
    fun `toDisplayMessage returns user-friendly messages`() {
        assertEquals("Connection timed out. Please try again.", JarvisError.NetworkError.timeout().toDisplayMessage())
        assertEquals("Server error. Please try again.", JarvisError.NetworkError.serverError(500, "Error").toDisplayMessage())
        assertEquals("Authentication error. Please re-login.", JarvisError.NetworkError.clientError(401, "Unauthorized").toDisplayMessage())
        assertEquals("Not found.", JarvisError.NetworkError.clientError(404, "Not found").toDisplayMessage())
        assertEquals("Network error. Check your connection.", JarvisError.NetworkError("Some error").toDisplayMessage())

        assertEquals("Audio error: Recording failed", JarvisError.AudioError.recordingFailed().toDisplayMessage())
        assertEquals("Voice activation error: Wake word initialization failed", JarvisError.WakeWordError.initFailed().toDisplayMessage())
        assertEquals("Permission required: android.permission.RECORD_AUDIO", JarvisError.PermissionError.microphone().toDisplayMessage())
        assertEquals("Setup error: API key not configured", JarvisError.ConfigurationError.missingApiKey("API key").toDisplayMessage())
        assertEquals("Please log in again", JarvisError.AuthError.tokenExpired().toDisplayMessage())
    }
}
