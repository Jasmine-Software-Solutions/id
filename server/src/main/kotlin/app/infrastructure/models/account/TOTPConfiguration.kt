package app.infrastructure.models.account

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.*

object TOTPConfigurationTable : UUIDTable("account_2fa_totp") {
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val confirmedAt = long("confirmed_at").nullable()

    val account = reference("account", AccountsTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val enabled = bool("enabled").default(false)

    val encryptedSecret = blob("encrypted_secret").nullable()

    val digits = integer("digits").nullable()
    val periodSeconds = long("period_seconds").nullable()
    val algorithm = varchar("algorithm", 10).nullable()
}

class TOTPConfiguration(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TOTPConfiguration>(TOTPConfigurationTable)

    var createdAt by TOTPConfigurationTable.createdAt
    var confirmedAt by TOTPConfigurationTable.confirmedAt

    var enabled by TOTPConfigurationTable.enabled
    var account by Account referencedOn TOTPConfigurationTable.account

    var encryptedSecret by TOTPConfigurationTable.encryptedSecret

    var digits by TOTPConfigurationTable.digits
    var periodSeconds by TOTPConfigurationTable.periodSeconds
    var algorithm by TOTPConfigurationTable.algorithm

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