package com.jasminesoftwaresolutions.idinterfaces.user.authentication

import com.jasminesoftwaresolutions.id.domain.models.account.IMagicLink
import com.jasminesoftwaresolutions.idinterfaces.renderWithContext
import com.jasminesoftwaresolutions.idinterfaces.services.MagicLinkControllerService
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.community.routing.annotations.Query
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import java.util.*

open class AjaxMagicLinkController<T : IMagicLink>(
    protected val service: MagicLinkControllerService<T>
) {
    @Get("/magic_links/{id}")
    fun renderPrompt(
        ctx: Context,
        @Param id: UUID,
        @Query("token") token: String
    ) {
        when (val result = service.prompt(id, token)) {
            is MagicLinkControllerService.PromptResult.Prompt -> {
                ctx.renderWithContext(
                    "pages/magic_link/prompt.kte",
                    "accountEmail" to result.accountEmail,
                    "id" to result.id,
                    "token" to result.token
                )
            }

            is MagicLinkControllerService.PromptResult.AlreadyDecided -> {
                renderResult(
                    ctx = ctx,
                    title = if (result.approved) "Already approved" else "Already denied",
                    detail = if (result.approved)
                        "This sign-in request has already been approved."
                    else
                        "This sign-in request has already been denied."
                )
            }

            is MagicLinkControllerService.PromptResult.Expired -> {
                renderResult(
                    ctx = ctx,
                    title = "Magic link expired",
                    detail = "This sign-in request has expired. Start a new sign-in to receive a fresh link."
                )
            }

            is MagicLinkControllerService.PromptResult.Invalid -> {
                renderResult(
                    ctx = ctx,
                    title = "Invalid magic link",
                    detail = "This sign-in link is invalid."
                )
            }
        }
    }

    @Post("/magic_links/{id}")
    fun decide(
        ctx: Context,
        @Param id: UUID,
        @Query("token") token: String
    ) {
        val decision = ctx.formParam("decision")
            ?: throw BadRequestResponse()

        val approve = when (decision) {
            "allow" -> true
            "deny" -> false
            else -> throw BadRequestResponse()
        }

        when (val result = service.decide(id, token, approve)) {
            is MagicLinkControllerService.DecideResult.Approved -> {
                renderResult(
                    ctx = ctx,
                    title = "Sign-in approved",
                    detail = "The sign-in request has been approved."
                )
            }

            is MagicLinkControllerService.DecideResult.Denied -> {
                renderResult(
                    ctx = ctx,
                    title = "Sign-in denied",
                    detail = "The sign-in request has been denied."
                )
            }

            is MagicLinkControllerService.DecideResult.AlreadyDecided -> {
                renderResult(
                    ctx = ctx,
                    title = if (result.approved) "Already approved" else "Already denied",
                    detail = if (result.approved)
                        "This sign-in request has already been approved."
                    else
                        "This sign-in request has already been denied."
                )
            }

            is MagicLinkControllerService.DecideResult.Expired -> {
                renderResult(
                    ctx = ctx,
                    title = "Magic link expired",
                    detail = "This sign-in request has expired. Start a new sign-in to receive a fresh link."
                )
            }

            is MagicLinkControllerService.DecideResult.Invalid -> {
                renderResult(
                    ctx = ctx,
                    title = "Invalid magic link",
                    detail = "This sign-in link is invalid."
                )
            }
        }
    }

    protected open fun renderResult(ctx: Context, title: String, detail: String) {
        ctx.renderWithContext(
            "pages/magic_link/result.kte",
            "title" to title,
            "detail" to detail
        )
    }
}
