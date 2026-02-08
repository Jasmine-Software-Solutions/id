package app.infrastructure.models.account

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.*

object TOTPConfigurationTable : UUIDTable("account_2fa_totp") {
    val account = reference("account", AccountsTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val enabled = bool("enabled").default(false)

    val encryptedSecret = blob("encrypted_secret")

    val digits = integer("digits")
    val periodSeconds = long("period_seconds")
    val algorithm = varchar("algorithm", 10)

    val confirmedAt = long("confirmed_at").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

class TOTPConfiguration(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TOTPConfiguration>(TOTPConfigurationTable)

    var enabled by TOTPConfigurationTable.enabled
    var account by Account referencedOn TOTPConfigurationTable.account

    var encryptedSecret by TOTPConfigurationTable.encryptedSecret

    var digits by TOTPConfigurationTable.digits
    var periodSeconds by TOTPConfigurationTable.periodSeconds
    var algorithm by TOTPConfigurationTable.algorithm

    var confirmedAt by TOTPConfigurationTable.confirmedAt
    var createdAt by TOTPConfigurationTable.createdAt
    var updatedAt by TOTPConfigurationTable.updatedAt

    override fun equals(other: Any?): Boolean {
        if (other !is TOTPConfiguration)
            return false

        return this.id.value == other.id.value
    }

    override fun hashCode(): Int {
        return id.value.hashCode()
    }
}

object TOTPUsageTable : LongIdTable("account_2fa_totp_usage") {
    val createdAt = long("created_at")

    val account = reference("account", AccountsTable, onDelete = ReferenceOption.CASCADE)
    val period = long("period")

    init {
        uniqueIndex(account, period)
    }
}