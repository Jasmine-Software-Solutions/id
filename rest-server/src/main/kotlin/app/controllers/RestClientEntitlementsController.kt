package app.controllers

import app.application.client.*
import app.infrastructure.etc.toUUIDOrNull
import io.javalin.community.routing.annotations.*
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.util.*

@Endpoints("/api/v1/client-entitlements")
class RestClientEntitlementsController(
    private val listHandler: ListClientEntitlementsHandler,
    private val upsertHandler: UpsertClientEntitlementHandler,
    private val deleteHandler: DeleteClientEntitlementHandler,
) {
    data class UpsertRequest(
        @get:com.fasterxml.jackson.annotation.JsonProperty("client_id") val clientId: UUID?,
        @get:com.fasterxml.jackson.annotation.JsonProperty("tenant") val tenantId: UUID?,
        @get:com.fasterxml.jackson.annotation.JsonProperty("scope") val scope: String?,
    )

    @Get
    fun list(ctx: Context, @Header("Authorization") authorization: String?) {
        val clientId = ctx.queryParam("client_id")?.toUUIDOrNull()
            ?: if (ctx.queryParam("client_id") != null) throw BadRequestResponse("Invalid client_id") else null

        val tenantParam = ctx.queryParam("tenant")
        val onlyWildcard = tenantParam == "*"
        val tenantId = when {
            tenantParam == null || onlyWildcard -> null
            else -> tenantParam.toUUIDOrNull() ?: throw BadRequestResponse("Invalid tenant")
        }

        when (val result = listHandler.execute(
            ListClientEntitlementsCommand(
                authorization = authorization,
                clientId = clientId,
                tenantId = tenantId,
                onlyWildcard = onlyWildcard,
            )
        )) {
            is ListClientEntitlementsResult.Success -> ctx.json(result.entitlements)
            is ListClientEntitlementsResult.Forbidden -> throw ForbiddenResponse()
        }
    }

    @Put
    fun upsert(ctx: Context, @Header("Authorization") authorization: String?) {
        val payload = runCatching { ctx.bodyAsClass(UpsertRequest::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")
        val clientId = payload.clientId ?: throw BadRequestResponse("Missing client_id")

        when (val result = upsertHandler.execute(
            UpsertClientEntitlementCommand(
                authorization = authorization,
                clientId = clientId,
                tenantId = payload.tenantId,
                scope = payload.scope,
            )
        )) {
            is UpsertClientEntitlementResult.Success -> ctx.json(result.entitlement)
            is UpsertClientEntitlementResult.Forbidden -> throw ForbiddenResponse()
            is UpsertClientEntitlementResult.ClientNotFound -> throw NotFoundResponse("Client not found")
            is UpsertClientEntitlementResult.TenantNotFound -> throw NotFoundResponse("Tenant not found")
        }
    }

    @Delete
    fun delete(ctx: Context, @Header("Authorization") authorization: String?) {
        val clientId = ctx.queryParam("client_id")?.toUUIDOrNull()
            ?: throw BadRequestResponse("Missing or invalid client_id")
        val tenantParam = ctx.queryParam("tenant")
            ?: throw BadRequestResponse("Missing tenant selector")
        val tenantId = when (tenantParam) {
            "*" -> null
            else -> tenantParam.toUUIDOrNull() ?: throw BadRequestResponse("Invalid tenant")
        }

        when (val result = deleteHandler.execute(
            DeleteClientEntitlementCommand(
                authorization = authorization,
                clientId = clientId,
                tenantId = tenantId,
            )
        )) {
            is DeleteClientEntitlementResult.Success -> ctx.status(204)
            is DeleteClientEntitlementResult.Forbidden -> throw ForbiddenResponse()
            is DeleteClientEntitlementResult.ClientNotFound -> throw NotFoundResponse("Client not found")
            is DeleteClientEntitlementResult.NotFound -> throw NotFoundResponse("Entitlement not found")
        }
    }
}
