package app.application.oauth2

import app.Env
import app.infrastructure.etc.SecureToken
import app.infrastructure.models.oauth2.SessionAccessToken
import java.time.Instant
import java.util.*

object SessionTokenAuthorizationService {
    fun isAccessTokenActive(token: SessionAccessToken) =
        token.session.isValid() && token.lastRefreshed.toEpochMilli() > System.currentTimeMillis() - Env.SESSION_ACCESS_TOKEN_LIFETIME * 1000

    fun authorizedFor(token: SessionAccessToken, scope: String, tenant: UUID?): Boolean {
        if (tenant != null && token.tenant.id.value != tenant)
            return false

        val scopes = token.scope?.split(" ") ?: return true
        val hasScope = scopes.contains(scope)
        return hasScope
    }

    fun isRefreshTokenActive(token: SessionAccessToken): Boolean {
        return token.session.isValid()
    }

    fun isAuthorizationCodeActive(token: SessionAccessToken): Boolean {
        return token.authorizationCodeExpiration?.isAfter(Instant.now()) ?: false
    }

    fun refreshAccessToken(token: SessionAccessToken) {
        if (!isRefreshTokenActive(token))
            throw IllegalStateException("Refresh token is not active")

        token.lastRefreshed = Instant.now()
        token.accessToken = SecureToken()
    }
}