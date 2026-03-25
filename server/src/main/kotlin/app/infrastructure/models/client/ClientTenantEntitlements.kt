package app.infrastructure.models.client

import app.infrastructure.models.tenant.Tenant
import app.infrastructure.models.tenant.TenantsTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.*

object ClientTenantEntitlementsTable : UUIDTable("client_tenant_entitlements") {
    val client = reference("client", ClientsTable, onDelete = ReferenceOption.CASCADE)
    val tenant = optReference("tenant", TenantsTable, onDelete = ReferenceOption.CASCADE)
    // "*" means wildcard entitlement that applies to all tenants.
    val tenantKey = varchar("tenant_key", 36).nullable()
    val scope = text("scope").nullable()

    init {
        // Enforces one row per concrete tenant and one wildcard row per client.
        uniqueIndex(client, tenantKey)
        index(false, client, tenant)
    }
}

class ClientTenantEntitlement(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ClientTenantEntitlement>(ClientTenantEntitlementsTable)

    var client by Client referencedOn ClientTenantEntitlementsTable.client
    var tenant by Tenant optionalReferencedOn ClientTenantEntitlementsTable.tenant
    var tenantKey by ClientTenantEntitlementsTable.tenantKey
    var scope by ClientTenantEntitlementsTable.scope
}
