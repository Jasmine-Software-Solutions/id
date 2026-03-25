package app.application.tenant

import app.application.authorization.APIAuthorizationEngine
import app.application.authorization.Policy
import app.application.authorization.PolicyResult
import app.infrastructure.models.oauth2.MachineAccessToken
import app.infrastructure.models.oauth2.SessionAccessToken
import app.infrastructure.models.tenant.Tenant
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

data class GetTenantInfoCommand(
    val authorization: String?,
    val tenantId: UUID?,
)

interface GetTenantInfoHandler {
    fun execute(command: GetTenantInfoCommand): GetTenantInfoResult
}

sealed class GetTenantInfoResult {
    data class Success(
        @get:JsonProperty("id") val id: UUID,
        @get:JsonProperty("name") val name: String,
        @get:JsonProperty("created_at") val createdAt: Long
    ) : GetTenantInfoResult()

    object NotFound : GetTenantInfoResult()
    object Forbidden : GetTenantInfoResult()
}

class GetTenantInfoService() : GetTenantInfoHandler {
    override fun execute(command: GetTenantInfoCommand): GetTenantInfoResult = transaction {
        val policyResult = APIAuthorizationEngine.evaluateAll(
            command.authorization,
            Policy.IsTokenActive,
            Policy.IsAdministrator(command.tenantId),
            Policy.HasScope(command.tenantId, "id:tenant:read")
        )

        if (policyResult !is PolicyResult.Pass) return@transaction GetTenantInfoResult.Forbidden
        if (command.tenantId == null && policyResult.token is MachineAccessToken)
            return@transaction GetTenantInfoResult.Forbidden

        val tenant = command.tenantId?.let(Tenant::findById)
            ?: (policyResult.token as? SessionAccessToken)?.tenant
            ?: return@transaction GetTenantInfoResult.NotFound

        GetTenantInfoResult.Success(
            tenant.id.value,
            tenant.name,
            tenant.createdAt.toEpochMilli()
        )
    }
}
