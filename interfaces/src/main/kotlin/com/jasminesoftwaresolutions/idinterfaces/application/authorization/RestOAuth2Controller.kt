package com.jasminesoftwaresolutions.idinterfaces.application.authorization

import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinAuthorizationService
import com.jasminesoftwaresolutions.idinterfaces.services.client.OAuth2ControllerService
import io.javalin.community.routing.annotations.Post
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.UnauthorizedResponse
import java.util.*

class RestOAuth2Controller(val service: OAuth2ControllerService, val authorizationService: JavalinAuthorizationService) {
    @Post("/api/v1/oauth2/token")
    fun token(ctx: Context) {
        val authentication = authorizationService.authenticate(ctx)
            ?: throw UnauthorizedResponse()

        val grantType = ctx.queryParam("grant_type")
        if (grantType == "authorization_code") {
            val clientId = ctx.queryParam("client_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw BadRequestResponse()

            val redirectUri = ctx.queryParam("redirect_uri")
                ?: throw BadRequestResponse()

            val code = ctx.queryParam("code")
                ?: throw BadRequestResponse()

            val result = service.getTokenWithCode(authentication, clientId, redirectUri, code)
            when (result) {
                is OAuth2ControllerService.TokenWithCodeResult.Granted -> {
                    ctx.json(
                        mapOf(
                            "token_type" to "Bearer",
                            "access_token" to result.accessToken,
                            "expires_in" to result.expiresIn,
                            "refresh_token" to result.refreshToken,
                            "refresh_token_expires_in" to result.refreshTokenExpiresIn,
                        )
                    )

                    ctx.status(200)
                }

                is OAuth2ControllerService.TokenWithCodeResult.Unauthorized -> throw ForbiddenResponse()

                else -> throw BadRequestResponse()
            }
        }

        else if (grantType == "client_credentials") {
            val scope = ctx.queryParam("scope")
            val tenantId = ctx.queryParam("tenant_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            val result = service.getTokenWithCredentials(authentication, tenantId, scope)

            when (result) {
                is OAuth2ControllerService.TokenWithCredentialsResult.Granted -> ctx.json(
                    mapOf(
                        "token_type" to "Bearer",
                        "access_token" to result.accessToken,
                        "expires_in" to result.expiresIn,
                    )
                )

                is OAuth2ControllerService.TokenWithCredentialsResult.Unauthorized -> throw ForbiddenResponse()

                else -> throw BadRequestResponse()
            }
        }

        else if (grantType == "refresh_token") {
            val refreshToken = ctx.queryParam("refresh_token")
                ?: throw BadRequestResponse()

            val result = service.getTokenWithRefreshToken(authentication, refreshToken)

            when (result) {
                is OAuth2ControllerService.TokenWithRefreshTokenResult.Granted -> ctx.json(
                    mapOf(
                        "token_type" to "Bearer",
                        "access_token" to result.accessToken,
                        "expires_in" to result.expiresIn,
                        "refresh_token" to result.refreshToken,
                        "refresh_token_expires_in" to result.refreshTokenExpiresIn,
                    )
                )

                is OAuth2ControllerService.TokenWithRefreshTokenResult.Unauthorized -> throw ForbiddenResponse()

                else -> throw BadRequestResponse()
            }
        }

        else throw BadRequestResponse()
    }
}