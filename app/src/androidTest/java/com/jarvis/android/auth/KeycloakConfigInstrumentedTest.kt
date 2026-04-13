package com.jarvis.android.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for KeycloakConfig
 * Runs on Android device/emulator where android.net.Uri is available
 */
@RunWith(AndroidJUnit4::class)
class KeycloakConfigInstrumentedTest {

    @Test
    fun clientId_isJarvisAndroid() {
        assertEquals("jarvis-android", KeycloakConfig.CLIENT_ID)
    }

    @Test
    fun realm_isJarvis() {
        assertEquals("jarvis", KeycloakConfig.REALM)
    }

    @Test
    fun keycloakUrl_isHttps() {
        assertTrue(
            "Keycloak URL should be HTTPS",
            KeycloakConfig.KEYCLOAK_URL.startsWith("https://")
        )
    }

    @Test
    fun redirectUri_hasCorrectScheme() {
        assertEquals("com.jarvis.android://oauth/callback", KeycloakConfig.REDIRECT_URI)
    }

    @Test
    fun postLogoutRedirectUri_matchesScheme() {
        assertTrue(
            "Post logout should use same scheme",
            KeycloakConfig.POST_LOGOUT_REDIRECT_URI.startsWith("com.jarvis.android://")
        )
    }

    @Test
    fun scopes_containsRequiredOidcScopes() {
        val scopes = KeycloakConfig.SCOPES
        assertTrue("Should contain openid", scopes.contains("openid"))
        assertTrue("Should contain profile", scopes.contains("profile"))
        assertTrue("Should contain email", scopes.contains("email"))
    }

    @Test
    fun issuerUri_pointsToRealm() {
        val uri = KeycloakConfig.ISSUER_URI
        assertTrue(
            "Issuer should contain realm path",
            uri.toString().contains("/realms/jarvis")
        )
    }

    @Test
    fun authorizationEndpoint_isValid() {
        val uri = KeycloakConfig.AUTHORIZATION_ENDPOINT
        assertTrue(
            "Authorization endpoint should end with /auth",
            uri.toString().endsWith("/auth")
        )
    }

    @Test
    fun tokenEndpoint_isValid() {
        val uri = KeycloakConfig.TOKEN_ENDPOINT
        assertTrue(
            "Token endpoint should end with /token",
            uri.toString().endsWith("/token")
        )
    }
}
