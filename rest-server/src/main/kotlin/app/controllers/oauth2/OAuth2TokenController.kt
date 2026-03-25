package app.controllers.oauth2

import app.application.oauth2.*
import io.javalin.community.routing.annotations.Get
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.UnauthorizedResponse
import java.util.*

class OAuth2TokenController(
    private val authorizationCodeToken: OAuth2AuthorizationCodeTokenHandler,
    private val refreshTokenService: OAuth2RefreshTokenHandler,
    private val clientCredentialsToken: OAuth2ClientCredentialsTokenHandler,
) {

    @Suppress("unused")
    @Get("/api/v1/oauth2/token")
    fun token(ctx: Context) {
        val grantType = ctx.queryParam("grant_type")
            ?: throw BadRequestResponse("Missing grant_type")

        when (grantType) {
            "authorization_code" -> authorizationCode(ctx)
            "refresh_token" -> refreshToken(ctx)
            "client_credentials" -> clientCredentials(ctx)
            else -> throw BadRequestResponse("Unsupported grant_type: $grantType")
        }
    }

    private fun authorizationCode(ctx: Context) {
        val code = ctx.queryParam("code") ?: throw BadRequestResponse("Missing code")
        val redirectUri = ctx.queryParam("redirect_uri") ?: throw BadRequestResponse("Missing redirect_uri")
        val clientIdParam = ctx.queryParam("client_id") ?: throw BadRequestResponse("Missing client_id")
        val clientId = runCatching { UUID.fromString(clientIdParam) }.getOrNull()
            ?: throw BadRequestResponse("Invalid client_id")

        val clientSecretParam = if (ctx.header("Authorization")?.startsWith("Basic ") == true) {
            val base64Credentials = ctx.header("Authorization")!!.substringAfter("Basic ").trim()
            val decodedCredentials = runCatching { String(Base64.getDecoder().decode(base64Credentials)) }.getOrNull()
                ?: throw BadRequestResponse("Invalid client credentials format")
            val parts = decodedCredentials.split(":", limit = 2)
            if (parts.size != 2) throw BadRequestResponse("Invalid client credentials format")

            val basicClientId = runCatching { UUID.fromString(parts[0]) }.getOrNull()
                ?: throw BadRequestResponse("Invalid client credentials format")
            if (basicClientId != clientId)
                throw BadRequestResponse("client_id does not match Authorization header")

            parts[1]
        } else null

        when (val result = authorizationCodeToken.execute(
            OAuth2AuthorizationCodeTokenCommand(
                code = code,
                redirectUri = redirectUri,
                clientId = clientId,
                clientSecret = clientSecretParam,
            )
        )) {
            is OAuth2AuthorizationCodeTokenResult.Success -> ctx.json(result)
            is OAuth2AuthorizationCodeTokenResult.InvalidCode -> throw BadRequestResponse("Invalid authorization code")
            is OAuth2AuthorizationCodeTokenResult.InvalidClient -> throw BadRequestResponse("Invalid client ID")
            is OAuth2AuthorizationCodeTokenResult.InvalidRedirectUri -> throw BadRequestResponse("Invalid redirect_uri")
            is OAuth2AuthorizationCodeTokenResult.CodeExpired -> throw BadRequestResponse("Authorization code expired")
            is OAuth2AuthorizationCodeTokenResult.Unauthorized -> throw UnauthorizedResponse()
        }
    }

    private fun refreshToken(ctx: Context) {
        val refreshToken = ctx.queryParam("refresh_token")
            ?: throw BadRequestResponse("Missing refresh_token")

        when (val result = refreshTokenService.execute(OAuth2RefreshTokenCommand(refreshToken))) {
            is OAuth2RefreshTokenResult.Success -> ctx.json(result)
            is OAuth2RefreshTokenResult.InvalidToken -> throw BadRequestResponse("Invalid refresh token")
            is OAuth2RefreshTokenResult.TokenExpired -> throw BadRequestResponse("Refresh token expired")
        }
    }

    private fun clientCredentials(ctx: Context) {
        val header = ctx.header("Authorization")
            ?: throw BadRequestResponse("Missing Authorization header")

        if (!header.startsWith("Basic "))
            throw BadRequestResponse("Invalid Authorization header format")

        val base64Credentials = header.substringAfter("Basic ").trim()
        val decodedCredentials = String(Base64.getDecoder().decode(base64Credentials))
        val parts = decodedCredentials.split(":", limit = 2)
        if (parts.size != 2) throw BadRequestResponse("Invalid client credentials format")

        val clientId = runCatching { UUID.fromString(parts[0]) }.getOrNull()
            ?: throw BadRequestResponse("Invalid client credentials format")
        val clientSecret = parts[1]
        val tenant = runCatching { UUID.fromString(ctx.queryParam("tenant")) }.getOrNull()
            ?: throw BadRequestResponse("Missing or invalid tenant")
        val requestedScope = ctx.queryParam("scope")

        when (val result = clientCredentialsToken.execute(
            OAuth2ClientCredentialsTokenCommand(
                clientId = clientId,
                clientSecret = clientSecret,
                tenantId = tenant,
                requestedScope = requestedScope,
            )
        )) {
            is OAuth2ClientCredentialsTokenResult.Success -> ctx.json(result)
            is OAuth2ClientCredentialsTokenResult.InvalidClient -> throw BadRequestResponse("Invalid client ID")
            is OAuth2ClientCredentialsTokenResult.InvalidSecret -> throw BadRequestResponse("Invalid client secret")
            is OAuth2ClientCredentialsTokenResult.InvalidTenant -> throw BadRequestResponse("Invalid tenant")
            is OAuth2ClientCredentialsTokenResult.TenantNotEntitled -> throw BadRequestResponse("Client is not entitled for tenant")
        }
    }
}
