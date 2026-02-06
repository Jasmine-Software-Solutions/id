package app.infrastructure.models.account

import app.infrastructure.etc.transformInstant
import app.infrastructure.etc.transformNullableInstant
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.*

object SessionsTable : UUIDTable("sessions") {
    val createdAt = long("created_at")
    val accessedAt = long("accessed_at")
    val expiresAt = long("expires_at")

    val invalidatedAt = long("invalidated_at").nullable()

    val account = reference("account", AccountsTable, onDelete = ReferenceOption.CASCADE)

    val userAgent = varchar("user_agent", 256)
    val ipAddress = varchar("ip_address", 39)

    val token = varchar("token", 32).uniqueIndex()
}

class Session(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Session>(SessionsTable)

    var createdAt by SessionsTable.createdAt.transformInstant()
    var accessedAt by SessionsTable.accessedAt.transformInstant()
    var expiresAt by SessionsTable.expiresAt.transformInstant()

    var invalidatedAt by SessionsTable.invalidatedAt.transformNullableInstant()

    var account by Account referencedOn SessionsTable.account

    var userAgent by SessionsTable.userAgent
    var ipAddress by SessionsTable.ipAddress

    var token by SessionsTable.token

    fun isValid(): Boolean {
        return invalidatedAt == null && expiresAt.toEpochMilli() > System.currentTimeMillis()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Session)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}

fun Session?.isNullOrInvalid(): Boolean {
    return this == null || !this.isValid()
}