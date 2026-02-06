package app.application.oauth2

import app.domain.OAuth2Authorized
import app.infrastructure.models.oauth2.AccessToken
import app.infrastructure.models.oauth2.MachineAccessToken
import app.infrastructure.models.oauth2.SessionAccessToken
import java.util.*

class MachineTokenPrincipal(
    private val token: MachineAccessToken,
    private val authz: MachineTokenAuthorizationService
) : OAuth2Authorized {
    override fun isAccessTokenActive() =
        authz.isAccessTokenActive(token)

    override fun authorizedFor(scope: String, tenant: UUID?) =
        authz.authorizedFor(token, scope, tenant)
}

class SessionTokenPrincipal(
    private val token: SessionAccessToken,
    private val authz: SessionTokenAuthorizationService
) : OAuth2Authorized {
    override fun isAccessTokenActive() =
        authz.isAccessTokenActive(token)

    override fun authorizedFor(scope: String, tenant: UUID?) =
        authz.authorizedFor(token, scope, tenant)

    fun isRefreshTokenActive(): Boolean {
        return authz.isRefreshTokenActive(token)
    }

    fun isAuthorizationCodeActive(): Boolean {
        return authz.isAuthorizationCodeActive(token)
    }

    fun refreshAccessToken() {
        authz.refreshAccessToken(token)
    }
}

fun AccessToken.principal(): OAuth2Authorized = when (this) {
    is MachineAccessToken -> this.principal()
    is SessionAccessToken -> this.principal()
    else -> throw IllegalStateException()
}

fun MachineAccessToken.principal() = MachineTokenPrincipal(this, MachineTokenAuthorizationService)
fun SessionAccessToken.principal() = SessionTokenPrincipal(this, SessionTokenAuthorizationService)