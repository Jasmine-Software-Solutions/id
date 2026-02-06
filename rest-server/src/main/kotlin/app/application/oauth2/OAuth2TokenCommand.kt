package app.application.oauth2

import app.Env
import app.infrastructure.etc.SecureToken
import app.infrastructure.models.client.Client
import app.infrastructure.models.client.ClientsTable
import app.infrastructure.models.oauth2.MachineAccessToken
import app.infrastructure.models.oauth2.MachineAccessTokensTable
import app.infrastructure.models.oauth2.SessionAccessToken
import app.infrastructure.models.oauth2.SessionAccessTokensTable
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

// ----- Authorization code grant -----

data class OAuth2AuthorizationCodeTokenCommand(
    val code: String,
    val redirectUri: String,
    val clientId: UUID,
    val clientCredentialsToken: String? = null,
)

interface OAuth2AuthorizationCodeTokenHandler {
    fun execute(command: OAuth2AuthorizationCodeTokenCommand): OAuth2AuthorizationCodeTokenResult
}

sealed class OAuth2AuthorizationCodeTokenResult {
    data class Success(
        @get:JsonProperty("access_token") val accessToken: String,
        @get:JsonProperty("refresh_token") val refreshToken: String,
        @get:JsonProperty("expires_in") val expiresIn: Long,
        @get:JsonProperty("refresh_token_expires_in") val refreshTokenExpiresIn: Long,
    ) : OAuth2AuthorizationCodeTokenResult()

    object InvalidCode : OAuth2AuthorizationCodeTokenResult()
    object InvalidClient : OAuth2AuthorizationCodeTokenResult()
    object InvalidRedirectUri : OAuth2AuthorizationCodeTokenResult()
    object CodeExpired : OAuth2AuthorizationCodeTokenResult()
    object Unauthorized : OAuth2AuthorizationCodeTokenResult()
}

class OAuth2AuthorizationCodeTokenService : OAuth2AuthorizationCodeTokenHandler {
    override fun execute(command: OAuth2AuthorizationCodeTokenCommand): OAuth2AuthorizationCodeTokenResult = transaction {
        val client = Client.find { ClientsTable.id eq command.clientId }.firstOrNull()
            ?: return@transaction OAuth2AuthorizationCodeTokenResult.InvalidClient

        if (client.confidential) {
            val clientToken = command.clientCredentialsToken
                ?: return@transaction OAuth2AuthorizationCodeTokenResult.Unauthorized
            val machineTokens = MachineAccessToken.find {
                MachineAccessTokensTable.accessToken eq clientToken
            }.firstOrNull() ?: return@transaction OAuth2AuthorizationCodeTokenResult.Unauthorized

            if (!machineTokens.principal().isAccessTokenActive())
                return@transaction OAuth2AuthorizationCodeTokenResult.Unauthorized
        }

        val sessionAccessToken = SessionAccessToken.find {
            SessionAccessTokensTable.authorizationCode eq command.code
        }.firstOrNull()
            ?: return@transaction OAuth2AuthorizationCodeTokenResult.InvalidCode

        if (sessionAccessToken.client != client)
            return@transaction OAuth2AuthorizationCodeTokenResult.InvalidClient

        if (!sessionAccessToken.principal().isAuthorizationCodeActive())
            return@transaction OAuth2AuthorizationCodeTokenResult.CodeExpired

        if (sessionAccessToken.redirectUri.uri != command.redirectUri)
            return@transaction OAuth2AuthorizationCodeTokenResult.InvalidRedirectUri

        sessionAccessToken.authorizationCode = null
        sessionAccessToken.authorizationCodeExpiration = null
        sessionAccessToken.lastRefreshed = Instant.now()

        OAuth2AuthorizationCodeTokenResult.Success(
            accessToken = sessionAccessToken.accessToken,
            refreshToken = sessionAccessToken.refreshToken!!,
            expiresIn = (sessionAccessToken.session.expiresAt.epochSecond - Instant.now().epochSecond)
                .coerceAtMost(Env.SESSION_ACCESS_TOKEN_LIFETIME),
            refreshTokenExpiresIn = sessionAccessToken.session.expiresAt.epochSecond - Instant.now().epochSecond,
        )
    }
}

// ----- Refresh token grant -----

data class OAuth2RefreshTokenCommand(val refreshToken: String)

interface OAuth2RefreshTokenHandler {
    fun execute(command: OAuth2RefreshTokenCommand): OAuth2RefreshTokenResult
}

sealed class OAuth2RefreshTokenResult {
    data class Success(
        @get:JsonProperty("access_token") val accessToken: String,
        @get:JsonProperty("refresh_token") val refreshToken: String,
        @get:JsonProperty("expires_in") val expiresIn: Long,
        @get:JsonProperty("refresh_token_expires_in") val refreshTokenExpiresIn: Long,
    ) : OAuth2RefreshTokenResult()

    object InvalidToken : OAuth2RefreshTokenResult()
    object TokenExpired : OAuth2RefreshTokenResult()
}

class OAuth2RefreshTokenService : OAuth2RefreshTokenHandler {
    override fun execute(command: OAuth2RefreshTokenCommand): OAuth2RefreshTokenResult = transaction {
        val sessionAccessToken = SessionAccessToken.find {
            SessionAccessTokensTable.refreshToken eq command.refreshToken
        }.firstOrNull()
            ?: return@transaction OAuth2RefreshTokenResult.InvalidToken

        if (!sessionAccessToken.principal().isRefreshTokenActive())
            return@transaction OAuth2RefreshTokenResult.TokenExpired

        val newAccessToken = SecureToken()
        val newRefreshToken = SecureToken()

        sessionAccessToken.lastRefreshed = Instant.now()
        sessionAccessToken.accessToken = newAccessToken
        sessionAccessToken.refreshToken = newRefreshToken

        OAuth2RefreshTokenResult.Success(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            expiresIn = (sessionAccessToken.session.expiresAt.epochSecond - Instant.now().epochSecond)
                .coerceAtMost(Env.SESSION_ACCESS_TOKEN_LIFETIME),
            refreshTokenExpiresIn = sessionAccessToken.session.expiresAt.epochSecond - Instant.now().epochSecond,
        )
    }
}

// ----- Client credentials grant -----

data class OAuth2ClientCredentialsTokenCommand(
    val clientId: UUID,
    val clientSecret: String,
    val requestedScope: String?,
)

interface OAuth2ClientCredentialsTokenHandler {
    fun execute(command: OAuth2ClientCredentialsTokenCommand): OAuth2ClientCredentialsTokenResult
}

sealed class OAuth2ClientCredentialsTokenResult {
    data class Success(
        @get:JsonProperty("access_token") val accessToken: String,
        @get:JsonProperty("expires_in") val expiresIn: Long,
        @get:JsonProperty("scope") val scope: String?,
    ) : OAuth2ClientCredentialsTokenResult()

    object InvalidClient : OAuth2ClientCredentialsTokenResult()
    object InvalidSecret : OAuth2ClientCredentialsTokenResult()
}

class OAuth2ClientCredentialsTokenService : OAuth2ClientCredentialsTokenHandler {
    override fun execute(command: OAuth2ClientCredentialsTokenCommand): OAuth2ClientCredentialsTokenResult = transaction {
        val client = Client.find { ClientsTable.id eq command.clientId }.firstOrNull()
            ?: return@transaction OAuth2ClientCredentialsTokenResult.InvalidClient

        if (client.secret != command.clientSecret)
            return@transaction OAuth2ClientCredentialsTokenResult.InvalidSecret

        val scope = if (client.scope == null) command.requestedScope else {
            val clientScopes = client.scope!!.split(" ")
            val requestedScopes = command.requestedScope?.split(" ") ?: clientScopes
            val validScopes = requestedScopes.filter { it in clientScopes }
            if (validScopes.isNotEmpty()) validScopes.joinToString(" ") else null
        }

        val accessToken = SecureToken()

        MachineAccessToken.new {
            this.client = client
            this.issuedAt = Instant.now()
            this.expiresAt = Instant.now().plus(1, ChronoUnit.DAYS)
            this.accessToken = accessToken
            this.scope = scope
        }

        OAuth2ClientCredentialsTokenResult.Success(
            accessToken = accessToken,
            expiresIn = 86400,
            scope = scope,
        )
    }
}
