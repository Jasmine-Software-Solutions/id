package app.controllers

import app.application.LoginCommand
import app.application.LoginHandler
import app.application.LoginMethod
import app.application.LoginResult
import app.controllers.oauth2.OAuth2Controller.Companion.oauth2Request
import app.controllers.oauth2.OAuth2Controller.Companion.redirectToOAuth2Authorize
import app.infrastructure.etc.exception.FormErrorException
import app.infrastructure.etc.hxRedirect
import app.infrastructure.etc.hxReswap
import app.infrastructure.etc.renderWithContext
import app.infrastructure.etc.toUUIDOrNull
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Post
import io.javalin.http.Context
import io.javalin.http.Cookie
import io.javalin.http.SameSite
import java.util.*

class LoginController(
    private val loginHandler: LoginHandler
) {
    @Post("/login")
    fun login(ctx: Context) {
        val tenant = ctx.formParam("tenant")?.let { UUID.fromString(it) }

        val formEmail = ctx.formParam("email")
        val formPassword = ctx.formParam("password")
        val formOtp = ctx.formParam("otp")?.replace("-", "")

        val formMagicLinkId = ctx.formParam("magic_link_id")?.toUUIDOrNull()
        val formMagicLinkToken = ctx.formParam("magic_link_token")?.toCharArray()

        if (formEmail.isNullOrBlank()) throw FormErrorException(
            summary = "Problem",
            detail = "You are missing required fields."
        )

        val method = if (formMagicLinkId != null)
            LoginMethod.MagicLink(formMagicLinkId, formMagicLinkToken)
        else if (formPassword != null) LoginMethod.Password(formPassword.toCharArray())
        else LoginMethod.MagicLink(null, null)

        val command = LoginCommand(
            email = formEmail,
            method = method,
            tenantId = tenant,
            otp = formOtp,
            ipAddress = ctx.ip(),
            userAgent = ctx.header("User-Agent") ?: "Unknown"
        )

        when (val result = loginHandler.execute(command)) {
            is LoginResult.Success -> {
                ctx.cookie(Cookie(
                    name = "session",
                    value = result.sessionToken,
                    isHttpOnly = true,
                    secure = true,
                    sameSite = SameSite.STRICT
                ))
                if (ctx.oauth2Request() == null) {
                    ctx.hxRedirect("/")
                } else {
                    redirectToOAuth2Authorize(ctx)
                }
            }
            is LoginResult.RequiresOtp -> {
                ctx.renderWithContext(
                    "components/login/enter_otp.kte"
                )
            }
            is LoginResult.InvalidCredentials -> throw FormErrorException(
                summary = "Problem",
                detail = "Invalid email or password."
            )
            is LoginResult.InvalidOtp -> throw FormErrorException(
                summary = "Problem",
                detail = "Invalid verification code."
            )
            is LoginResult.MagicLinkIssued -> {
                ctx.renderWithContext(
                    "components/login/magic_link/issued.kte",
                    "email" to command.email,
                    "magicLinkId" to result.magicLink.id.value,
                    "magicLinkToken" to result.magicLinkAcceptanceToken
                )
            }
            is LoginResult.MagicLinkPending -> {
                ctx.hxReswap("none")
                ctx.result("")
                ctx.status(204)
            }
        }
    }

    @Suppress("unused")
    @Get("/login")
    fun renderLogin(ctx: Context) {
        ctx.removeCookie("session")
        ctx.removeCookie("oauth2_request")

        val email = ctx.queryParam("email")?.ifBlank { null }
        val usingPassword = ctx.queryParam("password")?.equals("true") ?: false

        ctx.renderWithContext("pages/login.kte",
            "email" to email,
            "usingPassword" to usingPassword
        )
    }
}
