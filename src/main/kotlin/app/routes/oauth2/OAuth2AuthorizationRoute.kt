package app.routes.oauth2

import app.etc.*
import app.routes.LoginRoutes.requireSession
import app.sql.client.Client
import app.sql.oauth2.*
import app.sql.tenant.Tenant
import app.sql.tenant.TenantAccountLinksTable
import app.sql.tenant.TenantsTable
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Post
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.UnauthorizedResponse
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

object OAuth2AuthorizationRoute {
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

        val tenant = ctx.queryParam("tenant")?.let { UUID.fromString(it) }

        if (!isScopeSpecific(scope)) {
            throw BadRequestResponse("Invalid scope")
        }

        transaction {
            val client = Client.findById(clientId)
                ?: throw BadRequestResponse("Invalid client_id")

            @Suppress("NAME_SHADOWING")
            val redirectUri = client.redirectUris.firstOrNull { it.uri == redirectUri }
                ?: throw BadRequestResponse("Invalid redirect_uri")

            val session = try {
                ctx.requireSession()
            } catch (e: UnauthorizedResponse) {
                ctx.renderWithContext("pages/login.kte")
                return@transaction
            }

            if (tenant == null) {
                val linkedTenants = TenantAccountLinksTable.selectByAccount(session.account.id.value)
                    .map { it[TenantAccountLinksTable.tenant] }
                    .map { Tenant.findById(it)!! }

                val openboxTenants = Tenant.find { TenantsTable.openbox eq true }.toList()

                val tenants = if (session.account.systemAdmin) openboxTenants + linkedTenants else linkedTenants

                ctx.hxRetarget("body")
                ctx.renderWithContext("pages/oauth2/select.kte",
                    "account" to session.account,
                    "tenants" to tenants
                )

                return@transaction
            }

            @Suppress("NAME_SHADOWING")
            val tenant = Tenant.findById(tenant)
                ?: throw BadRequestResponse("Invalid tenant")

            val linkedTenants = TenantAccountLinksTable
                .select { TenantAccountLinksTable.account eq session.account.id }
                .map { it[TenantAccountLinksTable.tenant].value }

            if (!session.account.systemAdmin && !linkedTenants.contains(tenant.id.value))
                throw BadRequestResponse("Invalid tenant")

            if (!client.automaticGrant)
                throw UnsupportedOperationException("Explicit grant is not supported")

            val tokens = SessionAccessTokens.new {
                this.session = session
                this.client = client
                this.redirectUri = redirectUri

                this.accessToken = SecureToken()
                this.refreshToken = SecureToken()

                this.tenant = tenant
                this.scope = scope

                this.authorizationCode = SecureToken()
                this.authorizationCodeExpiration = Instant.now().plus(10, ChronoUnit.MINUTES)
            }

            val clientCodeUri = "${redirectUri.uri}?code=${tokens.authorizationCode}${if (state != null) "&state=$state" else ""}"
            ctx.hxRedirect(clientCodeUri)
        }
    }

    @Suppress("unused")
    @Post("/oauth2/select")
    fun selectTenant(ctx: Context) {
        ctx.requireSession()

        val responseType = ctx.formParam("response_type")
        if (responseType == null) {
            ctx.hxRedirect("/")
            return
        }

        ctx.redirectToOAuth2Authorize()
    }

    fun Context.requireAuthorization(): OAuth2Authorized {
        if (attribute<OAuth2Authorized>("oauth2_authorization") != null)
            return attribute("oauth2_authorization")!!

        val token = this.header("Authorization")
            ?: throw UnauthorizedResponse("Missing Authorization header")

        val accessToken = token.substringAfter("Bearer ").takeIf { it.isNotBlank() }
            ?: throw UnauthorizedResponse("Invalid Authorization header format")

        return transaction {
            val sessionAccessTokens = SessionAccessTokens.find { SessionAccessTokensTable.accessToken eq accessToken }.firstOrNull()
            val machineAccessTokens = MachineAccessTokens.find { MachineAccessTokensTable.accessToken eq accessToken }.firstOrNull()

            val tokens = sessionAccessTokens ?: machineAccessTokens
                ?: throw UnauthorizedResponse("Invalid access token")

            if (!tokens.isAccessTokenActive())
                throw UnauthorizedResponse("Access token has expired")

            attribute("oauth2_authorization", tokens)

            return@transaction tokens
        }
    }

    fun Context.redirectToOAuth2Authorize() {
        val queryParams = listOf(
            "response_type" to formParam("response_type"),
            "client_id" to formParam("client_id"),
            "redirect_uri" to formParam("redirect_uri"),
            "scope" to formParam("scope"),
            "state" to formParam("state"),
            "tenant" to formParam("tenant")
        ).filter { it.second != null }

        val oauth2AuthorizeUri = "/oauth2/authorize?${queryParams.joinToString("&") { "${it.first}=${it.second}" }}"
        hxRedirect(oauth2AuthorizeUri)
    }
}