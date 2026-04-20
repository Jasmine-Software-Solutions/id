package com.jasminesoftwaresolutions.idinterfaces.user.authentication

import com.google.gson.JsonObject
import com.jasminesoftwaresolutions.id.domain.models.SignedValue
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepRendererRegistry
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStepRenderer
import com.jasminesoftwaresolutions.id.domain.services.authentication.ContinueAuthenticationFlowStepResult
import com.jasminesoftwaresolutions.id.domain.services.authentication.RetryAuthenticationFlowStepResult
import com.jasminesoftwaresolutions.idinterfaces.services.LoginControllerService
import io.javalin.community.routing.annotations.Form
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Post
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.Cookie
import io.javalin.http.SameSite
import java.util.*

class AjaxLoginController<T : IAuthenticationFlow>(
    val htmlRendererRegistry: IAuthenticationStepRendererRegistry<T>,
    val service: LoginControllerService<T>
) {
    @Get("/login")
    fun renderLogin(ctx: Context) {
        val result = service.login()
        val renderer = htmlRendererRegistry.find(Context::class, result.step)
            as AuthenticationFlowStepRenderer<T, Context, Any>

        renderer.render(ctx, result.flow, result.step, result.request, ContinueAuthenticationFlowStepResult)
    }

    @Post("/login/next")
    fun handleLogin(
        ctx: Context,
        @Form("flow_id") flowId: UUID,
        @Form("request") request: String
    ) {
        val responseParams = ctx.formParamMap()
            .filter { it.key.startsWith("response.") }
            .mapKeys { it.key.substringAfter("response.") }
            .mapValues { it.value.first() }

        val responseObj = JsonObject().apply {
            responseParams.forEach { (key, value) -> addProperty(key, value) }
        }

        val result = service.next(flowId, request, responseObj)
        when (result) {
            is LoginControllerService.ContinueFlowNextResult -> {
                val renderer = htmlRendererRegistry.find(Context::class, result.step)
                        as AuthenticationFlowStepRenderer<T, Context, Any>

                val source = if (result is LoginControllerService.RetryFlowNextResult)
                    RetryAuthenticationFlowStepResult
                else ContinueAuthenticationFlowStepResult

                renderer.render(ctx, result.flow, result.step, result.request as SignedValue<Any>, source)
            }

            is LoginControllerService.FlowNotFoundNextResult, is LoginControllerService.SignatureMismatchNextResult -> {
                throw BadRequestResponse()
            }

            is LoginControllerService.AuthenticatedFlowNextResult -> {
                ctx.header("HX-Redirect", "/")

                ctx.cookie(Cookie(
                        name = "session_id",
                        value = result.session.id.toString(),
                        isHttpOnly = true,
                        secure = true,
                        sameSite = SameSite.STRICT
                    ))

                ctx.cookie(Cookie(
                    name = "session_token",
                    value = result.session.token,
                    isHttpOnly = true,
                    secure = true,
                    sameSite = SameSite.STRICT
                ))
            }
        }
    }
}