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
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

data class ListAccountsCommand(
    val authorization: String?,
    val tenantId: UUID?,
    val id: UUID? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val createdAtBefore: Instant? = null,
    val createdAtAfter: Instant? = null,
    val createdAtExact: Instant? = null,
    val matchAny: Boolean = false,
)

data class AccountDTO(
    @get:JsonProperty("id") val id: UUID,
    @get:JsonProperty("email") val email: String,
    @get:JsonProperty("first_name") val firstName: String,
    @get:JsonProperty("last_name") val lastName: String,
    @get:JsonProperty("created_at") val createdAt: Long,
    @get:JsonProperty("system_admin") val systemAdmin: Boolean,
)

interface ListAccountsHandler {
    fun execute(command: ListAccountsCommand): ListAccountsResult
}

sealed class ListAccountsResult {
    data class Success(
        val accounts: List<AccountDTO>
    ) : ListAccountsResult()

    object NotFound : ListAccountsResult()
    object Forbidden : ListAccountsResult()
}

class ListAccountsService() : ListAccountsHandler {
    override fun execute(command: ListAccountsCommand): ListAccountsResult = transaction {
        val policyResult = APIAuthorizationEngine.evaluateAll(
            command.authorization,
            Policy.IsTokenActive,
            Policy.IsAdministrator(command.tenantId),
            Policy.HasScope(command.tenantId, "id:accounts:read")
        )

        if (policyResult !is PolicyResult.Pass)
            return@transaction ListAccountsResult.Forbidden
        if (command.tenantId == null && policyResult.token is MachineAccessToken)
            return@transaction ListAccountsResult.Forbidden

        val tenant = command.tenantId?.let(Tenant::findById)
            ?: (policyResult.token as? SessionAccessToken)?.tenant
            ?: return@transaction ListAccountsResult.NotFound

        val tenantQuery = if (command.tenantId != null) TenantAccountLinksTable.tenant eq tenant.id else Op.TRUE

        val query = AccountsTable.innerJoin(TenantAccountLinksTable).select { tenantQuery }

        val conditions = mutableListOf<Op<Boolean>>()

        if (command.id != null)
            conditions.add(TenantAccountLinksTable.account eq command.id)

        if (command.email != null)
            conditions.add(AccountsTable.email like "%${command.email}%")

        if (command.firstName != null)
            conditions.add(AccountsTable.firstName like "%${command.firstName}%")

        if (command.lastName != null)
            conditions.add(AccountsTable.lastName like "%${command.lastName}%")

        if (command.createdAtBefore != null)
            conditions.add(AccountsTable.createdAt less command.createdAtBefore.toEpochMilli())

        if (command.createdAtAfter != null)
            conditions.add(AccountsTable.createdAt greater command.createdAtAfter.toEpochMilli())

        if (command.createdAtExact != null)
            conditions.add(AccountsTable.createdAt eq command.createdAtExact.toEpochMilli())

        if (conditions.isNotEmpty()) {
            if (command.matchAny) query.andWhere { conditions.reduce { acc, op -> acc or op } }
            else query.andWhere { conditions.reduce { acc, op -> acc and op } }
        }

        val accounts = query.map { Account.wrapRow(it) }

        if (accounts.isEmpty())
            return@transaction ListAccountsResult.NotFound

        ListAccountsResult.Success(accounts.map { account ->
            AccountDTO(
                id = account.id.value,
                email = account.email,
                firstName = account.firstName,
                lastName = account.lastName,
                createdAt = account.createdAt.toEpochMilli(),
                systemAdmin = account.systemAdmin,
            )
        })
    }
}
