package app.application.client

import app.application.authorization.APIAuthorizationEngine
import app.application.authorization.Policy
import app.application.authorization.PolicyResult
import app.infrastructure.models.client.Client
import app.infrastructure.models.client.ClientTenantEntitlement
import app.infrastructure.models.client.ClientTenantEntitlementsTable
import app.infrastructure.models.client.ClientsTable
import app.infrastructure.models.tenant.Tenant
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

private const val WILDCARD_TENANT_KEY = "*"

data class ClientEntitlementDTO(
    @get:JsonProperty("client_id") val clientId: UUID,
    @get:JsonProperty("tenant") val tenantId: UUID?,
    @get:JsonProperty("scope") val scope: String?,
)

private fun authorized(authorization: String?, scope: String): Boolean {
    val policyResult = APIAuthorizationEngine.evaluateAll(
        authorization,
        Policy.IsTokenActive,
        Policy.IsAdministrator(null),
        Policy.HasScope(null, scope),
    )
    return policyResult is PolicyResult.Pass
}

data class ListClientEntitlementsCommand(
    val authorization: String?,
    val clientId: UUID? = null,
    val tenantId: UUID? = null,
    val onlyWildcard: Boolean = false,
)

interface ListClientEntitlementsHandler {
    fun execute(command: ListClientEntitlementsCommand): ListClientEntitlementsResult
}

sealed class ListClientEntitlementsResult {
    data class Success(val entitlements: List<ClientEntitlementDTO>) : ListClientEntitlementsResult()
    object Forbidden : ListClientEntitlementsResult()
}

class ListClientEntitlementsService : ListClientEntitlementsHandler {
    override fun execute(command: ListClientEntitlementsCommand): ListClientEntitlementsResult = transaction {
        if (!authorized(command.authorization, "id:client_entitlements:read"))
            return@transaction ListClientEntitlementsResult.Forbidden

        var condition: Op<Boolean> = Op.TRUE
        command.clientId?.let { condition = condition and (ClientTenantEntitlementsTable.client eq it) }

        if (command.onlyWildcard) {
            condition = condition and (ClientTenantEntitlementsTable.tenant.isNull())
        } else {
            command.tenantId?.let { condition = condition and (ClientTenantEntitlementsTable.tenant eq it) }
        }

        val entitlements = ClientTenantEntitlement.find { condition }
            .map {
                ClientEntitlementDTO(
                    clientId = it.client.id.value,
                    tenantId = it.tenant?.id?.value,
                    scope = it.scope,
                )
            }

        ListClientEntitlementsResult.Success(entitlements)
    }
}

data class UpsertClientEntitlementCommand(
    val authorization: String?,
    val clientId: UUID,
    val tenantId: UUID?,
    val scope: String?,
)

interface UpsertClientEntitlementHandler {
    fun execute(command: UpsertClientEntitlementCommand): UpsertClientEntitlementResult
}

sealed class UpsertClientEntitlementResult {
    data class Success(val entitlement: ClientEntitlementDTO) : UpsertClientEntitlementResult()
    object Forbidden : UpsertClientEntitlementResult()
    object ClientNotFound : UpsertClientEntitlementResult()
    object TenantNotFound : UpsertClientEntitlementResult()
}

class UpsertClientEntitlementService : UpsertClientEntitlementHandler {
    override fun execute(command: UpsertClientEntitlementCommand): UpsertClientEntitlementResult = transaction {
        if (!authorized(command.authorization, "id:client_entitlements:write"))
            return@transaction UpsertClientEntitlementResult.Forbidden

        val client = Client.findById(command.clientId)
            ?: return@transaction UpsertClientEntitlementResult.ClientNotFound
        val tenant = command.tenantId?.let(Tenant::findById)
            ?: if (command.tenantId != null) return@transaction UpsertClientEntitlementResult.TenantNotFound else null

        val tenantKey = command.tenantId?.toString() ?: WILDCARD_TENANT_KEY
        val entitlement = ClientTenantEntitlement.find {
            ClientTenantEntitlementsTable.client eq client.id and
                (
                    (ClientTenantEntitlementsTable.tenantKey eq tenantKey) or
                        (ClientTenantEntitlementsTable.tenantKey.isNull() and when (tenant) {
                            null -> ClientTenantEntitlementsTable.tenant.isNull()
                            else -> ClientTenantEntitlementsTable.tenant eq tenant.id
                        })
                    )
        }.firstOrNull() ?: ClientTenantEntitlement.new {
            this.client = client
            this.tenant = tenant
            this.tenantKey = tenantKey
            this.scope = command.scope
        }

        entitlement.tenant = tenant
        entitlement.tenantKey = tenantKey
        entitlement.scope = command.scope

        UpsertClientEntitlementResult.Success(
            ClientEntitlementDTO(client.id.value, entitlement.tenant?.id?.value, entitlement.scope)
        )
    }
}

data class DeleteClientEntitlementCommand(
    val authorization: String?,
    val clientId: UUID,
    val tenantId: UUID?,
)

interface DeleteClientEntitlementHandler {
    fun execute(command: DeleteClientEntitlementCommand): DeleteClientEntitlementResult
}

sealed class DeleteClientEntitlementResult {
    object Success : DeleteClientEntitlementResult()
    object Forbidden : DeleteClientEntitlementResult()
    object ClientNotFound : DeleteClientEntitlementResult()
    object NotFound : DeleteClientEntitlementResult()
}

class DeleteClientEntitlementService : DeleteClientEntitlementHandler {
    override fun execute(command: DeleteClientEntitlementCommand): DeleteClientEntitlementResult = transaction {
        if (!authorized(command.authorization, "id:client_entitlements:write"))
            return@transaction DeleteClientEntitlementResult.Forbidden

        val client = Client.find { ClientsTable.id eq command.clientId }.firstOrNull()
            ?: return@transaction DeleteClientEntitlementResult.ClientNotFound

        val tenantKey = command.tenantId?.toString() ?: WILDCARD_TENANT_KEY
        val entitlement = ClientTenantEntitlement.find {
            ClientTenantEntitlementsTable.client eq client.id and
                (
                    (ClientTenantEntitlementsTable.tenantKey eq tenantKey) or
                        (ClientTenantEntitlementsTable.tenantKey.isNull() and when (command.tenantId) {
                            null -> ClientTenantEntitlementsTable.tenant.isNull()
                            else -> ClientTenantEntitlementsTable.tenant eq command.tenantId
                        })
                    )
        }.firstOrNull() ?: return@transaction DeleteClientEntitlementResult.NotFound

        entitlement.delete()
        DeleteClientEntitlementResult.Success
    }
}
