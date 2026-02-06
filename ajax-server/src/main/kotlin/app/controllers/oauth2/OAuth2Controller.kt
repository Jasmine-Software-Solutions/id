package app.controllers.oauth2

import app.application.oauth2.OAuth2AuthorizeCommand
import app.application.oauth2.OAuth2AuthorizeHandler
import app.application.oauth2.OAuth2AuthorizeResult
import app.infrastructure.etc.hxRedirect
import app.infrastructure.etc.hxRetarget
import app.infrastructure.etc.renderWithContext
import com.fasterxml.jackson.annotation.JsonProperty
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Post
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.Cookie
import java.util.*

class OAuth2Controller(
    private val oauth2AuthorizeHandler: OAuth2AuthorizeHandler
) {
    @Suppress("unused")
    @Get("/oauth2/authorize")
    fun authorize(ctx: Context) {
        val responseType = ctx.queryParam("response_type")
            ?: throw BadRequestResponse("Missing response_type")

        when (responseType) {
            "code" -> authorizeCode(ctx)
            else -> throw BadRequestResponse("Unsupported response_type: $responseType")
        }
    }

    private fun isScopeSpecific(scope: String?): Boolean {
        if (scope.isNullOrBlank()) return true

        val scopes = scope.split(" ")
        val tenantScopes = scopes.filter { it.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) }
            .map { it.substringBefore(":", it) }
        val uniqueTenants = tenantScopes.distinct()

        return uniqueTenants.size <= 1
    }

    private fun authorizeCode(ctx: Context) {
        val clientId = ctx.queryParam("client_id")?.let { UUID.fromString(it) }
            ?: throw BadRequestResponse("Missing client_id")

        val redirectUri = ctx.queryParam("redirect_uri")
            ?: throw BadRequestResponse("Missing redirect_uri")

        val scope = ctx.queryParam("scope")
        val state = ctx.queryParam("state")

        val tenantId = ctx.queryParam("tenant")?.let { UUID.fromString(it) }

        if (!isScopeSpecific(scope)) {
            throw BadRequestResponse("Invalid scope")
        }

        ctx.oauth2Request(AuthorizeEndpointSharedParameters(
            responseType = "code",
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            state = state,
            tenant = tenantId
        ))

        val command = OAuth2AuthorizeCommand(
            sessionToken = ctx.cookie("session"),
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            state = state,
            tenantId = tenantId
        )

        when (val result = oauth2AuthorizeHandler.execute(command)) {
            is OAuth2AuthorizeResult.Unauthorized -> {
                ctx.renderWithContext("pages/login.kte")
            }
            is OAuth2AuthorizeResult.InvalidClient -> throw BadRequestResponse("Invalid client_id")
            is OAuth2AuthorizeResult.InvalidRedirectUri -> throw BadRequestResponse("Invalid redirect_uri")
            is OAuth2AuthorizeResult.SelectTenant -> {
                ctx.hxRetarget("body")
                ctx.renderWithContext(
                    "pages/oauth2/select.kte",
                    "account" to result.account,
                    "tenants" to result.tenants
                )
            }
            is OAuth2AuthorizeResult.InvalidTenant -> throw BadRequestResponse("Invalid tenant")
            is OAuth2AuthorizeResult.Authorized -> ctx.hxRedirect(result.redirectUrl)
        }
    }

    @Suppress("unused")
    @Post("/oauth2/select")
    fun selectTenant(ctx: Context) {
        val tenant = runCatching { UUID.fromString(ctx.formParam("tenant")) }.getOrNull()
            ?: throw BadRequestResponse("Invalid tenant")

        redirectToOAuth2Authorize(ctx, tenant)
    }

    data class AuthorizeEndpointSharedParameters(
        @JsonProperty("responseType") val responseType: String = "code",
        @JsonProperty("clientId") val clientId: UUID,
        @JsonProperty("redirectUri") val redirectUri: String,
        @JsonProperty("scope") val scope: String?,
        @JsonProperty("state") val state: String?,
        @JsonProperty("tenant") val tenant: UUID?
    )

    companion object {
        fun Context.oauth2Request(): AuthorizeEndpointSharedParameters? =
            cookie("oauth2_request")?.let {
                jsonMapper().fromJsonString<AuthorizeEndpointSharedParameters>(
                    Base64.getDecoder().decode(it).decodeToString(),
                    AuthorizeEndpointSharedParameters::class.java
                )
            }

        fun Context.oauth2Request(parameters: AuthorizeEndpointSharedParameters) {
            cookie(Cookie(
                name = "oauth2_request",
                value = Base64.getEncoder().encodeToString(
                    jsonMapper().toJsonString(parameters, AuthorizeEndpointSharedParameters::class.java).toByteArray()
                ),
                maxAge = 120
            ))
        }

        fun redirectToOAuth2Authorize(ctx: Context, tenant: UUID? = null) {
            val request = ctx.oauth2Request()!!

            val queryParams = listOf(
                "response_type" to request.responseType,
                "client_id" to request.clientId,
                "redirect_uri" to request.redirectUri,
                "scope" to request.scope,
                "state" to request.state,
                "tenant" to (tenant ?: request.tenant)
            ).filter { it.second != null }

            val oauth2AuthorizeUri = "/oauth2/authorize?${queryParams.joinToString("&") { "${it.first}=${it.second}" }}"
            ctx.hxRedirect(oauth2AuthorizeUri)
        }
    }
}
