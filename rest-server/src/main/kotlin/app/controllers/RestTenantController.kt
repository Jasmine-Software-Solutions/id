package app.controllers

import app.application.tenant.GetTenantInfoCommand
import app.application.tenant.GetTenantInfoHandler
import app.application.tenant.GetTenantInfoResult
import app.infrastructure.etc.toUUIDOrNull
import io.javalin.community.routing.annotations.Endpoints
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Header
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse

@Endpoints("/api/v1/tenant")
class RestTenantController(
    private val getTenantInfoHandler: GetTenantInfoHandler
) {

    @Get
    fun info(ctx: Context, @Header("Authorization") authorization: String?) {
        val tenant = ctx.queryParam("tenant")?.toUUIDOrNull()

        when (val result = getTenantInfoHandler.execute(GetTenantInfoCommand(authorization, tenant))) {
            is GetTenantInfoResult.Success -> ctx.json(result)

            is GetTenantInfoResult.NotFound -> throw NotFoundResponse("Tenant not found")
            is GetTenantInfoResult.Forbidden -> throw ForbiddenResponse()
        }
    }
}
