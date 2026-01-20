package app.routes.oauth2

import app.Env
import app.models.oauth2.MachineAccessTokens
import app.models.oauth2.MachineAccessTokensTable
import app.models.oauth2.SessionAccessTokens
import app.models.oauth2.SessionAccessTokensTable
import app.routes.oauth2.OAuth2TokenRoute.requireAuthorization
import io.javalin.community.routing.annotations.Post
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction

object OAuth2IntrospectRoute {
    @Suppress("unused")
    @Post("/api/v1/oauth2/introspect")
    fun introspect(ctx: Context) {
        val authenticatingToken = ctx.requireAuthorization()

        val token = ctx.queryParam("token")
            ?: throw BadRequestResponse("Missing token")

        transaction {
            val sessionAccessTokens = SessionAccessTokens.find { SessionAccessTokensTable.accessToken eq token or
                    (SessionAccessTokensTable.refreshToken eq token)}.firstOrNull()
            val machineAccessTokens = MachineAccessTokens.find { MachineAccessTokensTable.accessToken eq token }.firstOrNull()

            val tokens = sessionAccessTokens ?: machineAccessTokens
            if (tokens == null) {
                ctx.json(mapOf("active" to false))
                return@transaction
            }

            val self = authenticatingToken == tokens
            if (!self && !authenticatingToken.authorizedFor("id:introspect"))
                throw ForbiddenResponse()

            val info = mutableMapOf<String, Any?>(
                "token_type" to "Bearer"
            )

            if (tokens is MachineAccessTokens) {
                info += mapOf<String, Any?>(
                    "active" to tokens.isAccessTokenActive(),
                    "scope" to tokens.scope,
                    "client_id" to tokens.client.id.value,
                    "exp" to tokens.expiresAt.epochSecond,
                    "iat" to tokens.issuedAt.epochSecond
                )
            } else if (tokens is SessionAccessTokens) {
                val isRefreshToken = token == tokens.refreshToken

                info += if (isRefreshToken) mapOf(
                    "active" to tokens.isRefreshTokenActive(),
                    "exp" to tokens.session.expiresAt.epochSecond
                ) else mapOf(
                    "active" to tokens.isAccessTokenActive(),
                    "exp" to (tokens.lastRefreshed.epochSecond + Env.SESSION_ACCESS_TOKEN_LIFETIME)
                        .coerceAtMost(tokens.session.expiresAt.epochSecond),
                )

                info += mapOf(
                    "scope" to tokens.scope,
                    "client_id" to tokens.client.id.value,
                    "iat" to tokens.issuedAt.epochSecond
                )
            }

            ctx.json(info)
        }
    }
}