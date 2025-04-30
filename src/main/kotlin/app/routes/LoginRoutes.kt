package app.routes

import app.Mail
import app.etc.EncryptedParameter
import app.etc.Token
import app.etc.exception.FormErrorException
import app.etc.hxRedirect
import app.etc.renderWithContext
import app.routes.oauth2.OAuth2AuthorizationRoute.redirectToOAuth2Authorize
import app.sql.account.*
import app.sql.audit.LoginAuditTable
import app.sql.tenant.TenantAccountLinksTable
import app.templateEngine
import de.mkammerer.argon2.Argon2Factory
import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import gg.jte.output.StringOutput
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Post
import io.javalin.http.Context
import io.javalin.http.UnauthorizedResponse
import org.apache.commons.codec.binary.Base32
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

object LoginRoutes {
    @Post("/login")
    fun login(ctx: Context) {
        fun renderGenericError(): Nothing {
            throw FormErrorException(
                summary = "Problem",
                detail = "Invalid email or password."
            )
        }

        val tenant = ctx.formParam("tenant")?.let { UUID.fromString(it) }

        val formEmail = ctx.formParam("email")
        val formPassword = ctx.formParam("password")?.let(EncryptedParameter::decrypt)
        val formOtp = ctx.formParam("otp")?.replace("-", "")

        if (setOf(formEmail, formPassword).any { it.isNullOrBlank() }) throw FormErrorException(
            summary = "Problem",
            detail = "You are missing required fields."
        )

        transaction {
            val account = Account.select(formEmail!!)

            if (account == null) {
                LoginAuditTable.write(ctx, "TERM No account found with provided email (${formEmail}).")
                renderGenericError()
            }

            LoginAuditTable.write(ctx, "FLOW Attempting login with email (${formEmail}) to account ${account.id.value} (${account.firstName} ${account.lastName}).")

            val linkedToTargetTenant = if (tenant == null) true else TenantAccountLinksTable.select {
                TenantAccountLinksTable.tenant eq tenant and (TenantAccountLinksTable.account eq account.id)
            }.count() >= 1 || account.systemAdmin

            if (!linkedToTargetTenant) {
                LoginAuditTable.write(ctx, "TERM Attempted to login to tenant (${tenant}), but no link found for account (${account.id.value}).")
                renderGenericError()
            }

            val password = Password.find(PasswordsTable.account eq account.id)
                .orderBy(PasswordsTable.createdAt to SortOrder.DESC)
                .firstOrNull()

            if (password == null) {
                LoginAuditTable.write(ctx, "TERM No password found for account (${account.id.value}).")
                renderGenericError()
            }

            val argon2 = Argon2Factory.create()
            val passwordArray = formPassword!!.toCharArray()

            val isPassword = try {
                argon2.verify(password.passwordHash, passwordArray)
            } finally {
                argon2.wipeArray(passwordArray)
            }

            if (!isPassword) {
                LoginAuditTable.write(ctx, "TERM Password mismatch for account (${account.id.value}).")
                renderGenericError()
            }

            LoginAuditTable.write(ctx, "FLOW Password verified for account (${account.id.value}).")

            if (account.totpSecret != null) {
                LoginAuditTable.write(ctx, "FLOW Account (${account.id.value}) has TOTP enabled.")

                if (formOtp.isNullOrBlank()) {
                    LoginAuditTable.write(ctx, "TERM No TOTP provided for account (${account.id.value}).")

                    ctx.renderWithContext("components/login/enter_otp.kte",
                        "email" to formEmail, "password" to formPassword)
                    return@transaction
                }

                val totpSecret = Base32().encode(account.totpSecret!!.toByteArray())
                val totp = setOf(
                    GoogleAuthenticator(totpSecret).generate(Date.from(Instant.now().minus(10, ChronoUnit.SECONDS))),
                    GoogleAuthenticator(totpSecret).generate(),
                    GoogleAuthenticator(totpSecret).generate(Date.from(Instant.now().plus(10, ChronoUnit.SECONDS))),
                )

                if (formOtp !in totp) {
                    LoginAuditTable.write(ctx, "TERM Invalid TOTP provided for account (${account.id.value}).")

                    throw FormErrorException(
                        summary = "Problem",
                        detail = "Invalid verification code."
                    )
                }

                LoginAuditTable.write(ctx, "FLOW Valid TOTP provided for account (${account.id.value}).")
            }

            val sessionToken = Token()

            val session = Session.new {
                this.createdAt = Instant.now()
                this.expiresAt = Instant.now().plus(3, ChronoUnit.HOURS)
                this.accessedAt = Instant.now()

                this.account = account
                this.token = sessionToken

                this.userAgent = ctx.header("User-Agent") ?: "Unknown"
                this.ipAddress = ctx.ip()
            }

            commit()

            LoginAuditTable.write(ctx, "FLOW Successful login for account (${account.id.value}).")
            LoginAuditTable.write(ctx, "TERM Generated session (${session.id.value}) for account (${account.id.value}).")

            ctx.cookie("session", sessionToken)

            val responseType = ctx.formParam("response_type")
            if (responseType == null) {
                ctx.hxRedirect("/")
                return@transaction
            }

            ctx.redirectToOAuth2Authorize()
        }
    }

    fun Context.requireSession(): Session {
        val session = cookie("session") ?: throw UnauthorizedResponse()

        return transaction {
            @Suppress("NAME_SHADOWING")
            val session = Session.find { SessionsTable.token eq session }.firstOrNull()

            if (session.isNullOrInvalid())
                throw UnauthorizedResponse()

            session!!.accessedAt = Instant.now()
            return@transaction session.load(Session::account)
        }
    }

    @Suppress("unused")
    @Get("/login")
    fun renderLogin(ctx: Context) {
        ctx.removeCookie("session")
        ctx.renderWithContext("pages/login.kte")
    }

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

        transaction {
            val account = Account.select(formEmail)

            // If tenant is set and account exists, where the account is either linked to the tenant or is a system admin
            val linkedToTargetTenant = tenant == null || account == null || TenantAccountLinksTable.select {
                TenantAccountLinksTable.tenant eq tenant and (TenantAccountLinksTable.account eq account.id)
            }.count() >= 1 || account.systemAdmin

            // If the account does not exist, or it is not linked, then deny any code
            // effectively hiding whether the account exists or is linked
            if (account == null || !linkedToTargetTenant) {
                if (!formCode.isNullOrBlank()) throw FormErrorException(
                    summary = "Problem",
                    detail = "Invalid code."
                ) else ctx.renderWithContext("components/login/forgot_password/enter_code.kte", "email" to formEmail)

                return@transaction
            }

            if (formCode.isNullOrBlank()) {
                // Generate code and email it
                val resetCode = (1..6).map { ('0'..'9').random() }.joinToString("")
                val resetCodeFormatted = resetCode.chunked(3).joinToString("-")

                ForgotPasswordCodesTable.insert {
                    it[this.account] = account.id.value
                    it[this.issuedAt] = System.currentTimeMillis()
                    it[this.expiresAt] = Instant.now().plus(30, ChronoUnit.MINUTES).toEpochMilli()
                    it[this.code] = resetCode
                }

                val templateOutput = StringOutput()
                templateEngine.render("emails/forgot_password.kte", resetCodeFormatted, templateOutput)

                val emailBody = templateOutput.toString()
                Mail.send(
                    to = formEmail,
                    subject = "[URGENT] Password Reset Request",
                    body = emailBody
                )

                ctx.renderWithContext("components/login/forgot_password/enter_code.kte", "email" to formEmail)
                return@transaction
            }

            val code = ForgotPasswordCodesTable.select { ForgotPasswordCodesTable.code eq formCode }.firstOrNull()
            if (code == null || System.currentTimeMillis() >= code[ForgotPasswordCodesTable.expiresAt]) throw FormErrorException(
                summary = "Problem",
                detail = "Invalid code."
            )

            if (formPassword.isNullOrBlank()) {
                ctx.renderWithContext("components/login/forgot_password/enter_password.kte", "email" to formEmail, "code" to formCode)
                return@transaction
            }

            if (formConfirmPassword.isNullOrBlank() || formPassword != formConfirmPassword) throw FormErrorException(
                summary = "Problem",
                detail = "Passwords do not match."
            )

            ForgotPasswordCodesTable.deleteWhere { ForgotPasswordCodesTable.code eq formCode }

            Password.new(account, formPassword)
            if (tenant == null) ctx.hxRedirect("/login")
            else ctx.hxRedirect("/login?tenant=$tenant")
        }
    }

    @Suppress("unused")
    @Get("/forgot_password")
    fun renderForgotPassword(ctx: Context) {
        val formEmail = ctx.queryParam("email") ?: ""
        ctx.renderWithContext("components/login/forgot_password/enter_email.kte", "email" to formEmail)
    }
}