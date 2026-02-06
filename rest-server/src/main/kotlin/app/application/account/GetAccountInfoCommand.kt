package app.application.account

import app.application.authorization.APIAuthorizationEngine
import app.application.authorization.Policy
import app.application.authorization.PolicyResult
import app.infrastructure.models.oauth2.SessionAccessToken
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

data class GetAccountInfoCommand(
    val authorization: String?,
    val tenantId: UUID?
)

interface GetAccountInfoHandler {
    fun execute(command: GetAccountInfoCommand): GetAccountInfoResult
}

sealed class GetAccountInfoResult {
    data class Success(
        @get:JsonProperty("id") val id: UUID,
        @get:JsonProperty("email") val email: String?,
        @get:JsonProperty("first_name") val firstName: String?,
        @get:JsonProperty("last_name") val lastName: String?,
        @get:JsonProperty("created_at") val createdAt: Long?,
        @get:JsonProperty("system_admin") val systemAdmin: Boolean?
    ) : GetAccountInfoResult()

    object Forbidden : GetAccountInfoResult()
}

private enum class GetAccountInfoScope(val scope: String) {
    EMAIL("email"),
    NAME("name"),
    CREATED_AT("created_at"),
    SYSTEM_ADMIN("system_admin")
}

class GetAccountInfoService : GetAccountInfoHandler {
    override fun execute(command: GetAccountInfoCommand): GetAccountInfoResult = transaction {
        var scopes = GetAccountInfoScope.entries.toSet()
            .associateWith { it -> "id:${it.scope}:read" }
            .mapValues { Policy.HasScope(command.tenantId, it.value) }

        val policy = Policy.And(listOf(
            Policy.IsTokenActive,
            Policy.IsAdministrator(command.tenantId),
            Policy.Or(scopes.values.toList())
        ))

        val policyResult = APIAuthorizationEngine.evaluate(command.authorization, policy)
        if (policyResult !is PolicyResult.Pass || policyResult.token !is SessionAccessToken)
            return@transaction GetAccountInfoResult.Forbidden

        val account = (policyResult.token as SessionAccessToken).session.account
        scopes = scopes.filterValues { policyResult.policies().contains(it) }

        val id = account.id.value

        val email = if (scopes.contains(GetAccountInfoScope.EMAIL))
            account.email else null

        val firstName = if (scopes.contains(GetAccountInfoScope.NAME))
            account.firstName else null

        val lastName = if (scopes.contains(GetAccountInfoScope.NAME))
            account.lastName else null

        val createdAt = if (scopes.contains(GetAccountInfoScope.CREATED_AT))
            account.createdAt.toEpochMilli() else null

        val systemAdmin = if (scopes.contains(GetAccountInfoScope.SYSTEM_ADMIN))
            account.systemAdmin else null

        GetAccountInfoResult.Success(id, email, firstName, lastName, createdAt, systemAdmin)
    }
}
