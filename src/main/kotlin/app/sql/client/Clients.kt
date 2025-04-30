package app.sql.client

import app.etc.transformInstant
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object ClientsTable : UUIDTable("clients") {
    val createdAt = long("created_at")
    val suspended = bool("suspended").default(false)

    val name = varchar("name", 64)
    val confidential = bool("confidential")

    val secret = varchar("secret", 64).nullable()

    val automaticGrant = bool("automatic_grant").default(false)

    val scope = text("scope").nullable()
}

@Suppress("unused")
class Client(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Client>(ClientsTable)

    var createdAt by ClientsTable.createdAt.transformInstant()
    var suspended by ClientsTable.suspended

    var name by ClientsTable.name
    var confidential by ClientsTable.confidential

    var secret by ClientsTable.secret

    var automaticGrant by ClientsTable.automaticGrant

    val redirectUris by ClientRedirectUri referrersOn ClientRedirectUrisTable.client

    var scope by ClientsTable.scope

    override fun equals(other: Any?): Boolean {
        if (other !is Client)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}