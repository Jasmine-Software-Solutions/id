package app.controllers

import app.application.ForgotPasswordCommand
import app.application.ForgotPasswordHandler
import app.application.ForgotPasswordResult
import app.infrastructure.etc.exception.FormErrorException
import app.infrastructure.etc.hxRedirect
import app.infrastructure.etc.renderWithContext
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Post
import io.javalin.http.Context
import java.util.*

class ForgotPasswordController(
    private val forgotPasswordHandler: ForgotPasswordHandler
) {
    @Suppress("unused")
    @Post("/forgot_password")
    fun forgotPassword(ctx: Context) {
        val tenant = ctx.formParam("tenant")?.let { UUID.fromString(it) }

        val formEmail = ctx.formParam("email")
        val formCode = ctx.formParam("code")?.replace("-", "")
        val formPassword = ctx.formParam("password")
        val formConfirmPassword = ctx.formParam("confirm_password")

        if (formEmail.isNullOrBlank()) throw FormErrorException(
            summary = "Problem",
            detail = "You are missing required fields."
        )

        val command = ForgotPasswordCommand(
            email = formEmail,
            code = formCode,
            password = formPassword,
            confirmPassword = formConfirmPassword,
            tenantId = tenant
        )

        when (val result = forgotPasswordHandler.execute(command)) {
            is ForgotPasswordResult.EnterCode -> {
                ctx.renderWithContext("components/login/forgot_password/enter_code.kte", "email" to result.email)
            }
            is ForgotPasswordResult.InvalidCode -> throw FormErrorException(
                summary = "Problem",
                detail = "Invalid code."
            )
            is ForgotPasswordResult.EnterPassword -> {
                ctx.renderWithContext(
                    "components/login/forgot_password/enter_password.kte",
                    "email" to result.email,
                    "code" to result.code
                )
            }
            is ForgotPasswordResult.PasswordsDoNotMatch -> throw FormErrorException(
                summary = "Problem",
                detail = "Passwords do not match."
            )
            is ForgotPasswordResult.Success -> {
                if (result.redirectTenantId == null) {
                    ctx.hxRedirect("/login")
                } else {
                    ctx.hxRedirect("/login?tenant=${result.redirectTenantId}")
                }
            }
        }
    }

    @Suppress("unused")
    @Get("/forgot_password")
    fun renderForgotPassword(ctx: Context) {
        val formEmail = ctx.queryParam("email") ?: ""
        ctx.renderWithContext("components/login/forgot_password/enter_email.kte", "email" to formEmail)
    }
}
