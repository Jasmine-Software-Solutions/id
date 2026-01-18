package app.routes.api

import app.models.tenant.Tenant
import io.javalin.community.routing.annotations.Endpoints
import io.javalin.community.routing.annotations.Get
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.transactions.transaction

@Endpoints("/api/v1/tenant")
object RestTenantRoutes {
    @Get
    fun info(ctx: Context) {
        transaction {
            ctx.requireAdministratorAndScope("id:tenant:read")

            val tenantId = ctx.tenantId
                ?: throw NotFoundResponse("Tenant not found")

            val tenant = Tenant.findById(tenantId)
                ?: throw NotFoundResponse("Tenant not found")

            val response = mapOf(
                "id" to tenant.id.value,
                "name" to tenant.name,
                "created_at" to tenant.createdAt.toEpochMilli(),
            )

            ctx.writeApiAudit("Tenant info requested; Returned: ${response.keys.joinToString(", ")}")

            ctx.json(response)
        }
    }
}