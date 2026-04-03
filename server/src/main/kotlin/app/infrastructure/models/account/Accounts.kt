package app.infrastructure.models.account

import app.infrastructure.etc.transformInstant
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object AccountsTable : UUIDTable("accounts") {
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

    val email = varchar("email", 320).uniqueIndex()

    val firstName = varchar("first_name", 40)
    val lastName = varchar("last_name", 40)

    val systemAdmin = bool("system_admin").default(false)
}

class Account(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Account>(AccountsTable) {
        fun select(email: String): Account? = transaction {
            return@transaction Account.find {
                AccountsTable.email eq email
            }.firstOrNull()
        }
    }

    var createdAt by AccountsTable.createdAt.transformInstant()

    var email by AccountsTable.email
    var firstName by AccountsTable.firstName
    var lastName by AccountsTable.lastName

    var systemAdmin by AccountsTable.systemAdmin

    val totpConfiguration by TOTPConfiguration optionalBackReferencedOn TOTPConfigurationTable.account

    override fun equals(other: Any?): Boolean {
        if (other !is Account)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}