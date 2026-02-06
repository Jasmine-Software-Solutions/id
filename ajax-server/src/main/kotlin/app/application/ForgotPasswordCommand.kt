package app.application

import app.application.password.IAccountPasswordUpdater
import app.infrastructure.ForgotPasswordEmailSender
import app.infrastructure.models.account.Account
import app.infrastructure.models.account.ForgotPasswordCodesTable
import app.infrastructure.models.tenant.TenantAccountLinksTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

data class ForgotPasswordCommand(
    val email: String,
    val code: String?,
    val password: String?,
    val confirmPassword: String?,
    val tenantId: UUID?
)

interface ForgotPasswordHandler {
    fun execute(command: ForgotPasswordCommand): ForgotPasswordResult
}

sealed class ForgotPasswordResult {
    data class EnterCode(val email: String) : ForgotPasswordResult()
    data class EnterPassword(val email: String, val code: String) : ForgotPasswordResult()
    data class Success(val redirectTenantId: UUID?) : ForgotPasswordResult()

    object InvalidCode : ForgotPasswordResult()
    object PasswordsDoNotMatch : ForgotPasswordResult()
}

class ForgotPasswordService(
    private val emailSender: ForgotPasswordEmailSender,
    private val accountPasswordUpdater: IAccountPasswordUpdater
) : ForgotPasswordHandler {
    override fun execute(command: ForgotPasswordCommand): ForgotPasswordResult = transaction {
        val account = Account.select(command.email)

        if (account == null) {
            if (!command.code.isNullOrBlank()) return@transaction ForgotPasswordResult.InvalidCode
            return@transaction ForgotPasswordResult.EnterCode(command.email)
        }

        val linkedToTargetTenant = command.tenantId == null || TenantAccountLinksTable.select {
            TenantAccountLinksTable.tenant eq command.tenantId and (TenantAccountLinksTable.account eq account.id)
        }.count() >= 1 || account.systemAdmin

        if (!linkedToTargetTenant) {
            if (!command.code.isNullOrBlank()) return@transaction ForgotPasswordResult.InvalidCode
            return@transaction ForgotPasswordResult.EnterCode(command.email)
        }

        if (command.code.isNullOrBlank()) {
            val resetCode = (1..6).map { ('0'..'9').random() }.joinToString("")
            val resetCodeFormatted = resetCode.chunked(3).joinToString("-")

            ForgotPasswordCodesTable.insert {
                it[ForgotPasswordCodesTable.account] = account.id.value
                it[ForgotPasswordCodesTable.issuedAt] = System.currentTimeMillis()
                it[ForgotPasswordCodesTable.expiresAt] = Instant.now().plus(30, ChronoUnit.MINUTES).toEpochMilli()
                it[ForgotPasswordCodesTable.code] = resetCode
            }

            emailSender.send(command.email, resetCodeFormatted)

            return@transaction ForgotPasswordResult.EnterCode(command.email)
        }

        val codeRow = ForgotPasswordCodesTable.select { ForgotPasswordCodesTable.code eq command.code }.firstOrNull()
        if (codeRow == null || System.currentTimeMillis() >= codeRow[ForgotPasswordCodesTable.expiresAt]) {
            return@transaction ForgotPasswordResult.InvalidCode
        }

        if (command.password.isNullOrBlank()) {
            return@transaction ForgotPasswordResult.EnterPassword(command.email, command.code)
        }

        if (command.confirmPassword.isNullOrBlank() || command.password != command.confirmPassword) {
            return@transaction ForgotPasswordResult.PasswordsDoNotMatch
        }

        ForgotPasswordCodesTable.deleteWhere { ForgotPasswordCodesTable.code eq command.code }

        accountPasswordUpdater.update(account, command.password)
        return@transaction ForgotPasswordResult.Success(redirectTenantId = command.tenantId)
    }
}