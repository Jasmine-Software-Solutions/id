package app.routes.api

import app.routes.oauth2.OAuth2AuthorizationRoute.requireAuthorization
import app.models.oauth2.SessionAccessTokens
import app.models.tenant.TenantAccountLinksTable
import io.javalin.community.routing.annotations.Endpoints
import io.javalin.community.routing.annotations.Get
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import org.jetbrains.exposed.sql.transactions.transaction

@Endpoints("/api/v1/account")
object RestAccountRoutes {
    @Suppress("unused")
    @Get
    fun info(ctx: Context) {
        val tokens = ctx.requireAuthorization()
        if (tokens !is SessionAccessTokens)
            throw ForbiddenResponse()

        transaction {
            val response = mutableMapOf<String, Any>(
                "id" to tokens.session.account.id.value
            )

            if (tokens.authorizedFor("id:email:read"))
                response["email"] = tokens.session.account.email

            if (tokens.authorizedFor("id:name:read")) {
                response["first_name"] = tokens.session.account.firstName
                response["last_name"] = tokens.session.account.lastName
            }

            if (tokens.authorizedFor("id:created_at:read")) {
                response["created_at"] = tokens.session.account.createdAt.toEpochMilli()
            }

            if (tokens.authorizedFor("id:system_admin:read")) {
                response["system_admin"] = tokens.session.account.systemAdmin
            }

            ctx.json(response)

            ctx.writeApiAudit("Account info requested; Returned: ${response.keys.joinToString(", ")}")
        }
    }

    @Suppress("unused")
    @Get("/links")
    fun links(ctx: Context) {
        val tokens = ctx.requireAuthorization()
        if (tokens !is SessionAccessTokens)
            throw ForbiddenResponse()

        if (!tokens.authorizedFor("id:account_links:read"))
            throw ForbiddenResponse()

        transaction {
            val links = TenantAccountLinksTable.selectByAccount(tokens.session.account.id.value)
            val dto = links.associate {
                it[TenantAccountLinksTable.tenant] to mapOf(
                    "administrator" to it[TenantAccountLinksTable.administrator]
                )
            }

            ctx.json(dto)

            ctx.writeApiAudit("Account links requested; Returned ${dto.size} links.")
        }
    }
}