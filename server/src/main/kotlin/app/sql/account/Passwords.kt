package app.sql.account

import app.Env
import app.etc.transformInstant
import de.mkammerer.argon2.Argon2Factory
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.time.Instant
import java.util.UUID

object PasswordsTable : UUIDTable("passwords") {
    val createdAt = long("created_at")

    val account = reference("account_id", AccountsTable, onDelete = ReferenceOption.CASCADE)
    val passwordHash = text("argon2_password_hash")
}

class Password(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Password>(PasswordsTable) {
        fun new(account: Account, password: String) {
            val argon2 = Argon2Factory.create(
                Env.PASSWORD_SALT_LENGTH,
                1024
            )

            val passwordArray = password.toCharArray()
            val hashedPassword = try {
                argon2.hash(
                    Env.PASSWORD_HASH_ITERATIONS,
                    Env.ARGON2_MEMORY,
                    Env.ARGON2_PARALLELISM,
                    passwordArray
                )
            } finally {
                argon2.wipeArray(passwordArray)
            }

            Password.new {
                this.createdAt = Instant.now()
                this.account = account
                this.passwordHash = hashedPassword
            }

            argon2.wipeArray(passwordArray)
        }
    }

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