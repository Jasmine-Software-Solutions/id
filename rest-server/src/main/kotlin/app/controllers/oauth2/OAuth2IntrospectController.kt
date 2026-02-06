package app.controllers.oauth2

import app.application.oauth2.OAuth2IntrospectCommand
import app.application.oauth2.OAuth2IntrospectHandler
import app.application.oauth2.OAuth2IntrospectResult
import io.javalin.community.routing.annotations.Header
import io.javalin.community.routing.annotations.Post
import io.javalin.community.routing.annotations.Query
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse

class OAuth2IntrospectController(
    private val introspectService: OAuth2IntrospectHandler
) {

    @Suppress("unused")
    @Post("/api/v1/oauth2/introspect")
    fun introspect(ctx: Context, @Header("Authorization") authorization: String?, @Query token: String) {
        val command = OAuth2IntrospectCommand(authorization, token)
        val result = introspectService.execute(command)

        when (result) {
            is OAuth2IntrospectResult.Success -> ctx.json(result)
            is OAuth2IntrospectResult.Forbidden -> throw ForbiddenResponse()
        }
    }
}
