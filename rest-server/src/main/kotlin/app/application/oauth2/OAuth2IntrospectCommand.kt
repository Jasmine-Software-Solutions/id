package app.application.oauth2

import app.Env
import app.application.authorization.APIAuthorizationEngine
import app.application.authorization.Policy
import app.application.authorization.PolicyResult
import app.infrastructure.models.oauth2.*
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

data class OAuth2IntrospectCommand(
    val authorization: String?,
    val token: String,
)

interface OAuth2IntrospectHandler {
    fun execute(command: OAuth2IntrospectCommand): OAuth2IntrospectResult
}

sealed class OAuth2IntrospectResult {
    data class Success(
        @get:JsonProperty("token_type") val tokenType: String,
        @get:JsonProperty("active") val active: Boolean,
        @get:JsonProperty("scope") val scope: String? = null,
        @get:JsonProperty("client_id") val clientId: UUID? = null,
        @get:JsonProperty("exp") val expiresAt: Long? = null,
        @get:JsonProperty("iat") val issuedAt: Long? = null
    ) : OAuth2IntrospectResult()

    object Forbidden : OAuth2IntrospectResult()
}

class OAuth2IntrospectService : OAuth2IntrospectHandler {
    override fun execute(command: OAuth2IntrospectCommand): OAuth2IntrospectResult = transaction {
        val sessionAccessTokens = SessionAccessToken.find {
            SessionAccessTokensTable.accessToken eq command.token or
                (SessionAccessTokensTable.refreshToken eq command.token)
        }.firstOrNull()

        val machineAccessTokens = MachineAccessToken.find {
            MachineAccessTokensTable.accessToken eq command.token
        }.firstOrNull()

        val tokens = sessionAccessTokens ?: machineAccessTokens
        if (tokens == null)
            return@transaction OAuth2IntrospectResult.Success("Bearer", false)

        // IsTokenActive AND (HasScope OR Is)
        val policyResult = APIAuthorizationEngine.evaluateAll(command.authorization,
            Policy.IsTokenActive, Policy.Or(listOf(
                Policy.HasScope(null, "id:introspect"),
                object : Policy<AccessToken> {
                    override fun evaluate(token: AccessToken): PolicyResult<AccessToken> {
                        if (token == tokens)
                            return PolicyResult.Pass(this, token)
                        return PolicyResult.Fail()
                    }
                }
        )))

        if (policyResult !is PolicyResult.Pass)
            return@transaction OAuth2IntrospectResult.Forbidden

        return@transaction when (tokens) {
            is MachineAccessToken -> {
                OAuth2IntrospectResult.Success(
                    "Bearer",
                    tokens.principal().isAccessTokenActive(),
                    tokens.scope,
                    tokens.client.id.value,
                    tokens.expiresAt.epochSecond,
                    tokens.issuedAt.epochSecond
                )
            }

            is SessionAccessToken -> {
                val isRefreshToken = command.token == tokens.refreshToken
                if (isRefreshToken) OAuth2IntrospectResult.Success(
                        "Bearer",
                        tokens.principal().isRefreshTokenActive(),
                        tokens.scope,
                        tokens.client.id.value,
                        tokens.session.expiresAt.epochSecond,
                        tokens.issuedAt.epochSecond
                    )
                else OAuth2IntrospectResult.Success(
                    "Bearer",
                    tokens.principal().isAccessTokenActive(),
                    tokens.scope,
                    tokens.client.id.value,
                    (tokens.lastRefreshed.epochSecond + Env.SESSION_ACCESS_TOKEN_LIFETIME)
                        .coerceAtMost(tokens.session.expiresAt.epochSecond),
                    tokens.issuedAt.epochSecond,
                )
            }

            else -> throw IllegalStateException()
        }
    }
}
