package app.infrastructure.models.account

import app.infrastructure.etc.transformInstant
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.*

object PasswordsTable : UUIDTable("passwords") {
    val createdAt = long("created_at")

    val account = reference("account_id", AccountsTable, onDelete = ReferenceOption.CASCADE)
    val passwordHash = text("argon2_password_hash")
}

class Password(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Password>(PasswordsTable)

    var createdAt by PasswordsTable.createdAt.transformInstant()

    var account by Account referencedOn PasswordsTable.account
    var passwordHash by PasswordsTable.passwordHash

    override fun equals(other: Any?): Boolean {
        if (other !is Password)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}