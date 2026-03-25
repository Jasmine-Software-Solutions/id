package app.application.accounts

import app.application.authorization.APIAuthorizationEngine
import app.application.authorization.Policy
import app.application.authorization.PolicyResult
import app.infrastructure.models.account.Account
import app.infrastructure.models.account.AccountsTable
import app.infrastructure.models.oauth2.MachineAccessToken
import app.infrastructure.models.oauth2.SessionAccessToken
import app.infrastructure.models.tenant.Tenant
import app.infrastructure.models.tenant.TenantAccountLinksTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

data class GetAccountCommand(
    val authorization: String?,
    val tenantId: UUID?,
    val accountId: UUID,
)

interface GetAccountHandler {
    fun execute(command: GetAccountCommand): GetAccountResult
}

sealed class GetAccountResult {
    data class Success(
        val account: AccountDTO
    ) : GetAccountResult()

    object NotFound : GetAccountResult()
    object Forbidden : GetAccountResult()
}

class GetAccountService() : GetAccountHandler {
    override fun execute(command: GetAccountCommand): GetAccountResult = transaction {
        val policyResult = APIAuthorizationEngine.evaluateAll(
            command.authorization,
            Policy.IsTokenActive,
            Policy.IsAdministrator(command.tenantId),
            Policy.HasScope(command.tenantId, "id:accounts:read")
        )

        if (policyResult !is PolicyResult.Pass)
            return@transaction GetAccountResult.Forbidden
        if (command.tenantId == null && policyResult.token is MachineAccessToken)
            return@transaction GetAccountResult.Forbidden

        val tenant = command.tenantId?.let(Tenant::findById)
            ?: (policyResult.token as? SessionAccessToken)?.tenant
            ?: return@transaction GetAccountResult.NotFound

        val account = AccountsTable.innerJoin(TenantAccountLinksTable)
            .select {
                TenantAccountLinksTable.tenant eq tenant.id and (AccountsTable.id eq command.accountId)
            }
            .map { Account.wrapRow(it) }
            .firstOrNull()
            ?: return@transaction GetAccountResult.NotFound

        GetAccountResult.Success(
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
