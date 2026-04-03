package app.application.accounts

import app.application.authorization.APIAuthorizationEngine
import app.application.authorization.Policy
import app.application.authorization.PolicyResult
import app.infrastructure.models.account.Account
import app.infrastructure.models.oauth2.MachineAccessToken
import app.infrastructure.models.oauth2.SessionAccessToken
import app.infrastructure.models.tenant.TenantAccountLinksTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

data class CreateAccountCommand(
    val authorization: String?,
    val email: String,
    val firstName: String,
    val lastName: String,
)

interface CreateAccountHandler {
    fun execute(command: CreateAccountCommand): CreateAccountResult
}

sealed class CreateAccountResult {
    data class Success(val account: AccountDTO) : CreateAccountResult()
    object Forbidden : CreateAccountResult()
}

class CreateAccountService : CreateAccountHandler {
    override fun execute(command: CreateAccountCommand): CreateAccountResult = transaction {
        val policyResult = APIAuthorizationEngine.evaluateAll(
            command.authorization,
            Policy.IsTokenActive,
            Policy.IsAdministrator(),
            Policy.HasScope(null, "id:accounts:create"),
        )

        if (policyResult !is PolicyResult.Pass)
            return@transaction CreateAccountResult.Forbidden

        val tenant = when (val token = policyResult.token) {
            is MachineAccessToken -> token.tenant.id
            is SessionAccessToken -> token.tenant.id
            else -> throw IllegalStateException()
        }

        val account = Account.select(command.email) ?: Account.new {
            createdAt = Instant.now()
            email = command.email
            firstName = command.firstName
            lastName = command.lastName
            systemAdmin = false
        }

        val alreadyLinked = TenantAccountLinksTable.select {
            TenantAccountLinksTable.tenant eq tenant and
                (TenantAccountLinksTable.account eq account.id)
        }.firstOrNull() != null

        if (!alreadyLinked) {
            TenantAccountLinksTable.insert {
                it[this.tenant] = tenant
                it[this.account] = account.id
                it[administrator] = false
            }
        }

        CreateAccountResult.Success(
            AccountDTO(
                id = account.id.value,
                email = account.email,
                firstName = account.firstName,
                lastName = account.lastName,
                createdAt = account.createdAt.toEpochMilli(),
                systemAdmin = account.systemAdmin,
            )
        )
    }
}
