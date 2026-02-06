package app.infrastructure.models.client

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.*

object ClientRedirectUrisTable : UUIDTable("client_redirect_uris") {
    val client = reference("client", ClientsTable, onDelete = ReferenceOption.CASCADE)
    val uri = varchar("uri", 2048)
}

class ClientRedirectUri(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ClientRedirectUri>(ClientRedirectUrisTable)

    var client by Client referencedOn ClientRedirectUrisTable.client
    var uri by ClientRedirectUrisTable.uri

    override fun equals(other: Any?): Boolean {
        if (other !is ClientRedirectUri)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}