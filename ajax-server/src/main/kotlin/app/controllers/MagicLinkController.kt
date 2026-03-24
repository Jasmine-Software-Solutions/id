package app.controllers

import app.application.MagicLinkCommand
import app.application.MagicLinkDecision
import app.application.MagicLinkHandler
import app.application.MagicLinkResult
import app.infrastructure.etc.renderWithContext
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Post
import io.javalin.http.Context

class MagicLinkController(
    private val magicLinkHandler: MagicLinkHandler
) {
    @Suppress("unused")
    @Get("/magic_links/{token}")
    fun renderDecision(ctx: Context) {
        val token = ctx.pathParam("token")
        val result = magicLinkHandler.execute(MagicLinkCommand(token, null))
        renderResult(ctx, result, token)
    }

    @Suppress("unused")
    @Post("/magic_links/{token}")
    fun submitDecision(ctx: Context) {
        val token = ctx.pathParam("token")
        val decision = when (ctx.formParam("decision")) {
            "allow" -> MagicLinkDecision.Allow
            "deny" -> MagicLinkDecision.Deny
            else -> null
        }

        if (decision == null) {
            ctx.status(400)
            ctx.renderWithContext("pages/status/4xx.kte")
            return
        }

        val result = magicLinkHandler.execute(MagicLinkCommand(token, decision))
        renderResult(ctx, result, token)
    }

    private fun renderResult(ctx: Context, result: MagicLinkResult, token: String) {
        when (result) {
            is MagicLinkResult.Prompt -> ctx.renderWithContext(
                "pages/magic_link/prompt.kte",
                "accountEmail" to result.accountEmail,
                "token" to token
            )
            is MagicLinkResult.Approved -> ctx.renderWithContext(
                "pages/magic_link/result.kte",
                "title" to "Attempt Approved",
                "detail" to "You can return to your original browser to finish signing in."
            )
            is MagicLinkResult.Denied -> ctx.renderWithContext(
                "pages/magic_link/result.kte",
                "title" to "Attempt Denied",
                "detail" to "If this was not you, your account remains protected."
            )
            is MagicLinkResult.InvalidOrExpired -> ctx.renderWithContext(
                "pages/magic_link/result.kte",
                "title" to "Link Invalid or Expired",
                "detail" to "Request a new magic link to continue."
            )
        }
    }
}
