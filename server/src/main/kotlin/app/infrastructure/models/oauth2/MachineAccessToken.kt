package app.infrastructure.models.oauth2

import app.infrastructure.etc.transformInstant
import app.infrastructure.models.client.Client
import app.infrastructure.models.client.ClientsTable
import app.infrastructure.models.tenant.Tenant
import app.infrastructure.models.tenant.TenantsTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.*

object MachineAccessTokensTable : UUIDTable("machine_access_tokens") {
    val client = reference("client", ClientsTable, onDelete = ReferenceOption.CASCADE)
    val tenant = reference("tenant", TenantsTable, onDelete = ReferenceOption.CASCADE)

    val issuedAt = long("issued_at").clientDefault { System.currentTimeMillis() }
    val expiresAt = long("expires_at")

    val accessToken = varchar("access_token", 64).uniqueIndex()
    val scope = varchar("scope", 256).nullable()
}

class MachineAccessToken(id: EntityID<UUID>) : UUIDEntity(id), AccessToken {
    companion object : UUIDEntityClass<MachineAccessToken>(MachineAccessTokensTable)

    var client by Client referencedOn MachineAccessTokensTable.client
    var tenant by Tenant referencedOn MachineAccessTokensTable.tenant

    var issuedAt by MachineAccessTokensTable.issuedAt.transformInstant()
    var expiresAt by MachineAccessTokensTable.expiresAt.transformInstant()

    var accessToken by MachineAccessTokensTable.accessToken
    var scope by MachineAccessTokensTable.scope

    override fun equals(other: Any?): Boolean {
        if (other !is MachineAccessToken)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}
