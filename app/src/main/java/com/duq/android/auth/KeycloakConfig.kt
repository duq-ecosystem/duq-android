package com.duq.android.auth

import android.net.Uri
import com.duq.android.BuildConfig

/**
 * Keycloak OIDC configuration
 * Uses Gateway HTTPS reverse proxy for mobile OAuth
 *
 * All server-specific values come from BuildConfig (configurable per build variant)
 */
object KeycloakConfig {
    // Keycloak via Gateway reverse proxy (HTTPS) - from BuildConfig
    val KEYCLOAK_URL: String = BuildConfig.KEYCLOAK_URL
    val REALM: String = BuildConfig.KEYCLOAK_REALM
    val CLIENT_ID: String = BuildConfig.KEYCLOAK_CLIENT_ID

    // OAuth2 endpoints (proxied through Gateway)
    val ISSUER_URI: Uri get() = Uri.parse("$KEYCLOAK_URL/realms/$REALM")
    val AUTHORIZATION_ENDPOINT: Uri get() = Uri.parse("$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/auth")
    val TOKEN_ENDPOINT: Uri get() = Uri.parse("$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token")
    val USERINFO_ENDPOINT: Uri get() = Uri.parse("$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/userinfo")
    val END_SESSION_ENDPOINT: Uri get() = Uri.parse("$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/logout")

    // Redirect URI (custom scheme - must match manifestPlaceholders)
    const val REDIRECT_URI = "com.duq.android://oauth/callback"
    const val POST_LOGOUT_REDIRECT_URI = "com.duq.android://oauth/logout"

    // Scopes
    val SCOPES = listOf("openid", "profile", "email")
}
