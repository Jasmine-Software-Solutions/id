package app.application

import app.infrastructure.account.AccountTOTPEngine
import app.infrastructure.etc.SecureToken
import app.infrastructure.models.account.Account
import app.infrastructure.models.account.Password
import app.infrastructure.models.account.PasswordsTable
import app.infrastructure.models.account.Session
import app.infrastructure.models.tenant.TenantAccountLinksTable
import de.mkammerer.argon2.Argon2Factory
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

data class LoginCommand(
    val email: String,
    val password: CharArray,
    val tenantId: UUID?,
    val otp: String?,
    val ipAddress: String,
    val userAgent: String
)

interface LoginHandler {
    fun execute(command: LoginCommand): LoginResult
}

sealed class LoginResult {
    data class Success(val sessionToken: String) : LoginResult()

    object RequiresOtp : LoginResult()
    object InvalidCredentials : LoginResult()
    object InvalidOtp : LoginResult()
}

interface LoginAuditLogger {
    fun log(ipAddress: String, action: String)
}

class LoginService(
    private val auditLogger: LoginAuditLogger
) : LoginHandler {
    override fun execute(command: LoginCommand): LoginResult = transaction {
        val account = Account.select(command.email)

        if (account == null) {
            auditLogger.log(command.ipAddress, "TERM No account found with provided email (${command.email}).")
            return@transaction LoginResult.InvalidCredentials
        }

        auditLogger.log(
            command.ipAddress,
            "FLOW Attempting login with email (${command.email}) to account ${account.id.value} (${account.firstName} ${account.lastName})."
        )

        val linkedToTargetTenant = if (command.tenantId == null) true else TenantAccountLinksTable.select {
            TenantAccountLinksTable.tenant eq command.tenantId and (TenantAccountLinksTable.account eq account.id)
        }.count() >= 1 || account.systemAdmin

        if (!linkedToTargetTenant) {
            auditLogger.log(
                command.ipAddress,
                "TERM Attempted to login to tenant (${command.tenantId}), but no link found for account (${account.id.value})."
            )
            return@transaction LoginResult.InvalidCredentials
        }

        val password = Password.find(PasswordsTable.account eq account.id)
            .orderBy(PasswordsTable.createdAt to SortOrder.DESC)
            .firstOrNull()

        if (password == null) {
            auditLogger.log(command.ipAddress, "TERM No password found for account (${account.id.value}).")
            return@transaction LoginResult.InvalidCredentials
        }

        val argon2 = Argon2Factory.create()
        val isPassword = try {
            argon2.verify(password.passwordHash, command.password)
        } finally {
            argon2.wipeArray(command.password)
        }

        if (!isPassword) {
            auditLogger.log(command.ipAddress, "TERM Password mismatch for account (${account.id.value}).")
            return@transaction LoginResult.InvalidCredentials
        }

        auditLogger.log(command.ipAddress, "FLOW Password verified for account (${account.id.value}).")

        if (account.totpConfiguration != null) {
            auditLogger.log(command.ipAddress, "FLOW Account (${account.id.value}) has TOTP enabled.")

            if (command.otp.isNullOrBlank()) {
                auditLogger.log(command.ipAddress, "TERM No TOTP provided for account (${account.id.value}).")
                return@transaction LoginResult.RequiresOtp
            }

            val valid = AccountTOTPEngine.consume(account, command.otp)
            if (!valid) {
                auditLogger.log(command.ipAddress, "TERM Invalid TOTP provided for account (${account.id.value}).")
                return@transaction LoginResult.InvalidOtp
            }

            auditLogger.log(command.ipAddress, "FLOW Valid TOTP provided for account (${account.id.value}).")
        }

        val sessionToken = SecureToken()

        val session = Session.new {
            this.createdAt = Instant.now()
            this.expiresAt = Instant.now().plus(3, ChronoUnit.HOURS)
            this.accessedAt = Instant.now()

            this.account = account
            this.token = sessionToken

            this.userAgent = command.userAgent
            this.ipAddress = command.ipAddress
        }

        commit()

        auditLogger.log(command.ipAddress, "FLOW Successful login for account (${account.id.value}).")
        auditLogger.log(
            command.ipAddress,
            "TERM Generated session (${session.id.value}) for account (${account.id.value})."
        )

        LoginResult.Success(sessionToken = sessionToken)
    }
}
