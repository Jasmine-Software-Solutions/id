package app.application.account

import app.application.authorization.APIAuthorizationEngine
import app.application.authorization.Policy
import app.application.authorization.PolicyResult
import app.infrastructure.models.oauth2.AccessToken
import app.infrastructure.models.oauth2.SessionAccessToken
import app.infrastructure.models.tenant.TenantAccountLinksTable
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

data class GetAccountLinksCommand(
    val authorization: String?
)

interface GetAccountLinksHandler {
    fun execute(command: GetAccountLinksCommand): GetAccountLinksResult
}

data class TenantLinkDTO(
    @get:JsonProperty("administrator") val administrator: Boolean
)

sealed class GetAccountLinksResult {
    data class Success(
        val links: Map<UUID, TenantLinkDTO>
    ) : GetAccountLinksResult()

    object Forbidden : GetAccountLinksResult()
}

class GetAccountLinksService : GetAccountLinksHandler {
    override fun execute(command: GetAccountLinksCommand): GetAccountLinksResult = transaction {
        val policyResult = APIAuthorizationEngine.evaluateAll(command.authorization,
            Policy.IsTokenActive,
            Policy.HasScope(null, "id:account_links:read"),
            object : Policy<AccessToken> {
                override fun evaluate(token: AccessToken): PolicyResult<AccessToken> {
                    if (token is SessionAccessToken)
                        return PolicyResult.Pass(this, token)
                    return PolicyResult.Fail()
                }
            })

        if (policyResult !is PolicyResult.Pass)
            return@transaction GetAccountLinksResult.Forbidden
        val token = policyResult.token as SessionAccessToken

        val links = TenantAccountLinksTable.selectByAccount(token.session.account.id.value)
        val dto = links.associate {
            it[TenantAccountLinksTable.tenant].value to TenantLinkDTO(
                it[TenantAccountLinksTable.administrator]
            )
        }

        GetAccountLinksResult.Success(dto)
    }
}
