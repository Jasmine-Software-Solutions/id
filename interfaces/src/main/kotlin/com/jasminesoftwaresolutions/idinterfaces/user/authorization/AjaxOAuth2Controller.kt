package com.jasminesoftwaresolutions.idinterfaces.user.authorization

import com.jasminesoftwaresolutions.idinterfaces.renderWithContext
import com.jasminesoftwaresolutions.idinterfaces.services.OAuth2ControllerService
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Post
import io.javalin.community.routing.annotations.Query
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import java.util.*

class AjaxOAuth2Controller(val service: OAuth2ControllerService) {
    private fun Context.requireLogin() {
        renderWithContext("pages/oauth2/login.kte")
    }

    private fun Context.buildRequest(): OAuth2ControllerService.OAuth2Request {
        val clientId = queryParam("client_id")?.let { UUID.fromString(it) }
            ?: throw BadRequestResponse()

        val redirectUri = queryParam("redirect_uri")
            ?: throw BadRequestResponse()

        val scope = queryParam("scope")
            ?: throw BadRequestResponse()

        val tenantId = queryParam("tenant_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val state = queryParam("state")

        val request = if (queryParam("tenant_id") == null) OAuth2ControllerService.OAuth2Request(
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            state = state
        ) else OAuth2ControllerService.TenantOAuth2Request(
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            state = state,
            tenantId = tenantId
        )

        return request
    }

    @Get("/oauth2/authorize")
    fun renderAuthorize(ctx: Context, @Query("response_type") responseType: String) {
        if (responseType != "code")
            throw BadRequestResponse()

        val sessionId = ctx.cookie("session_id")?.let { UUID.fromString(it) }
            ?: return ctx.requireLogin()

        val sessionToken = ctx.cookie("session_token")
            ?: return ctx.requireLogin()

        val result = service.authorize(
            sessionId,
            sessionToken,
            ctx.buildRequest()
        )

        when (result) {
            is OAuth2ControllerService.InvalidClientAuthorizeResult, is OAuth2ControllerService.InvalidTenantAuthorizeResult, is OAuth2ControllerService.InvalidRedirectUriAuthorizeResult -> {
                throw BadRequestResponse()
            }

            is OAuth2ControllerService.LoginRequiredAuthorizeResult -> {
                ctx.requireLogin()
            }

            is OAuth2ControllerService.SelectTenantAuthorizeResult -> {
                ctx.renderWithContext(
                    "pages/oauth2/select.kte",
                    "tenants" to result.tenants
                )
            }

            is OAuth2ControllerService.ConsentRequiredAuthorizeResult -> {
                ctx.renderWithContext(
                    "pages/oauth2/consent.kte",
                    "client" to result.client,
                    "account" to result.account,
                    "tenant" to result.tenant,
                    "scopes" to result.scopes
                )
            }
        }
    }

    @Post("/oauth2/authorize")
    fun authorize(ctx: Context, @Query("response_type") responseType: String) {
        if (responseType != "code")
            throw BadRequestResponse()

        val sessionId = ctx.cookie("session_id")?.let { UUID.fromString(it) }
            ?: return ctx.requireLogin()

        val sessionToken = ctx.cookie("session_token")
            ?: return ctx.requireLogin()

        val result = service.consent(
            sessionId,
            sessionToken,
            ctx.buildRequest()
        )

        when (result) {
            is OAuth2ControllerService.InvalidConsentResult -> {
                throw BadRequestResponse()
            }

            is OAuth2ControllerService.LoginRequiredConsentResult -> {
                ctx.requireLogin()
            }

            is OAuth2ControllerService.ConsentedConsentResult -> {
                ctx.header("HX-Redirect", result.redirectUri)
            }
        }
    }
}