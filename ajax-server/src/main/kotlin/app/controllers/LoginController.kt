package app.controllers

import app.application.LoginCommand
import app.application.LoginHandler
import app.application.LoginResult
import app.controllers.oauth2.OAuth2Controller.Companion.oauth2Request
import app.controllers.oauth2.OAuth2Controller.Companion.redirectToOAuth2Authorize
import app.infrastructure.etc.EncryptedParameter
import app.infrastructure.etc.exception.FormErrorException
import app.infrastructure.etc.hxRedirect
import app.infrastructure.etc.renderWithContext
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
        val formPassword = ctx.formParam("password")?.let(EncryptedParameter::decrypt)
        val formOtp = ctx.formParam("otp")?.replace("-", "")

        if (setOf(formEmail, formPassword).any { it.isNullOrBlank() }) throw FormErrorException(
            summary = "Problem",
            detail = "You are missing required fields."
        )

        val command = LoginCommand(
            email = formEmail!!,
            password = formPassword!!.toCharArray(),
            tenantId = tenant,
            otp = formOtp,
            ipAddress = ctx.ip(),
            userAgent = ctx.header("User-Agent") ?: "Unknown",
            encryptedPasswordForRetry = ctx.formParam("password")
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
                    "components/login/enter_otp.kte",
                    "email" to result.email,
                    "password" to result.passwordFormValue
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
        }
    }

    @Suppress("unused")
    @Get("/login")
    fun renderLogin(ctx: Context) {
        ctx.removeCookie("session")
        ctx.removeCookie("oauth2_request")

        ctx.renderWithContext("pages/login.kte")
    }
}
