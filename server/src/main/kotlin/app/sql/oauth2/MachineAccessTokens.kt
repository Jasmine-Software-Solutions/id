package app.sql.oauth2

import app.etc.transformInstant
import app.sql.client.Client
import app.sql.client.ClientsTable
import app.sql.tenant.Tenant
import app.sql.tenant.TenantsTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.time.Instant
import java.util.*

object MachineAccessTokensTable : UUIDTable("machine_access_tokens") {
    val client = reference("client", ClientsTable, onDelete = ReferenceOption.CASCADE)

    val issuedAt = long("issued_at").clientDefault { System.currentTimeMillis() }
    val expiresAt = long("expires_at")

    val accessToken = varchar("access_token", 64).uniqueIndex()
    val scope = varchar("scope", 256).nullable()
}

class MachineAccessTokens(id: EntityID<UUID>) : UUIDEntity(id), OAuth2Authorized {
    companion object : UUIDEntityClass<MachineAccessTokens>(MachineAccessTokensTable)

    var client by Client referencedOn MachineAccessTokensTable.client

    var issuedAt by MachineAccessTokensTable.issuedAt.transformInstant()
    var expiresAt by MachineAccessTokensTable.expiresAt.transformInstant()

    var accessToken by MachineAccessTokensTable.accessToken
    var scope by MachineAccessTokensTable.scope

    override fun isAccessTokenActive(): Boolean {
        return issuedAt.isBefore(Instant.now()) && Instant.now().isBefore(expiresAt)
    }

    override fun authorizedFor(scope: String, tenant: UUID?): Boolean {
        val scopes = this.scope?.split(" ") ?: return true
        val hasScope = scopes.contains(scope)
        return hasScope
    }

    override fun equals(other: Any?): Boolean {
        if (other !is MachineAccessTokens)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}