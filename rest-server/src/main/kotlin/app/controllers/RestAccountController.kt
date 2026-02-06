package app.controllers

import app.application.account.*
import app.infrastructure.etc.toUUIDOrNull
import io.javalin.community.routing.annotations.Endpoints
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Header
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse

@Endpoints("/api/v1/account")
class RestAccountController(
    private val getAccountInfo: GetAccountInfoHandler,
    private val getAccountLinks: GetAccountLinksHandler
) {

    @Suppress("unused")
    @Get
    fun info(ctx: Context, @Header("Authorization") authorization: String?) {
        val tenant = ctx.queryParam("tenant")?.toUUIDOrNull()

        when (val result = getAccountInfo.execute(GetAccountInfoCommand(authorization, tenant))) {
            is GetAccountInfoResult.Success -> ctx.json(result)
            is GetAccountInfoResult.Forbidden -> throw ForbiddenResponse()
        }
    }

    @Suppress("unused")
    @Get("/links")
    fun links(ctx: Context, @Header("Authorization") authorization: String?) {
        when (val result = getAccountLinks.execute(GetAccountLinksCommand(authorization))) {
            is GetAccountLinksResult.Success -> ctx.json(result.links)
            is GetAccountLinksResult.Forbidden -> throw ForbiddenResponse()
        }
    }
}
