package app.infrastructure.repositories.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IEncryptedTOTPConfiguration
import app.domain.models.account.ITOTPConfiguration
import app.domain.repositories.IAccountRepository
import app.domain.repositories.ITOTPConfigurationRepository
import app.domain.services.IEncryptionFunction
import app.infrastructure.entities.ExposedEntity
import app.infrastructure.models.account.TOTPConfigurationTable
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.ExposedColumnTransformer
import app.infrastructure.util.NullableInstantTransformer
import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ExposedTOTPConfigurationRepository(
    val encryptionService: IEncryptionFunction,
    val accountRepository: IAccountRepository
) : ITOTPConfigurationRepository {
    override fun findByAccount(id: UUID): ITOTPConfiguration = transaction {
        val row = Table.select { Table.account eq id }.firstOrNull()
            ?: return@transaction create(id)

        if (row[Table.enabled])
            return@transaction SetTOTPConfiguration(row, null, null)

        return@transaction TOTPConfiguration(row, null, null)
    }

    private fun create(id: UUID): ITOTPConfiguration = transaction {
        Table.insert {
            it[Table.account] = id
            it[Table.createdAt] = System.currentTimeMillis()
            it[Table.enabled] = false

            it[Table.confirmedAt] = null
            it[Table.digits] = null
            it[Table.periodSeconds] = null
            it[Table.algorithm] = null
            it[Table.encryptedSecret] = null
        }

        return@transaction findByAccount(id)
    }

    override fun update(entity: ITOTPConfiguration, function: ITOTPConfiguration.() -> Unit) = transaction {
        val isExposedEntity = entity is TOTPConfiguration
        val oldWrappedEntity = if (isExposedEntity) entity as TOTPConfiguration else null

        Table.update({ Table.account eq entity.account.id }) {
            val row = oldWrappedEntity?.row

            val newWrappedEntity = SetTOTPConfiguration(row, null, it)
            function(newWrappedEntity)

            if (isExposedEntity) {
                oldWrappedEntity?.row = row
                oldWrappedEntity?.insert = null
                oldWrappedEntity?.update = null
            }

            if (!entity.enabled) {
                it[Table.confirmedAt] = null
                it[Table.digits] = null
                it[Table.periodSeconds] = null
                it[Table.algorithm] = null
                it[Table.encryptedSecret] = null
            }
        }

        Unit
    }

    override fun install() {
        transaction { SchemaUtils.createMissingTablesAndColumns(TOTPConfigurationTable) }
    }

    open inner class TOTPConfiguration(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedEntity(row, insert, update), ITOTPConfiguration {
        override var account: IAccount by column(Table.account,
            EntityTransformer(ExposedAccountRepository.Table, accountRepository))

        override var enabled: Boolean by column(Table.enabled)
    }

    inner class SetTOTPConfiguration(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : TOTPConfiguration(row, insert, update), IEncryptedTOTPConfiguration {
        override var confirmedAt: Instant? by nullableColumn(Table.confirmedAt, NullableInstantTransformer)

        override var digits: Int by requiredColumn(Table.digits)
        override var period: Duration by requiredColumn(Table.periodSeconds, ExposedColumnTransformer(
            fromColumn = { it!!.toDuration(DurationUnit.SECONDS) },
            toColumn = { it.inWholeSeconds },
        ))

        override var algorithm: HmacAlgorithm by requiredColumn(Table.algorithm, ExposedColumnTransformer(
            fromColumn = { HmacAlgorithm.valueOf(it!!) },
            toColumn = { it.name },
        ))

        override var secret: ByteArray by requiredColumn(Table.encryptedSecret,
            transformer = ExposedColumnTransformer(
                fromColumn = { encryptionService.decrypt(it!!.bytes) },
                toColumn = { ExposedBlob(encryptionService.encrypt(it)) }
            ))
    }

    object Table : UUIDTable("account_2fa_totp") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val confirmedAt = long("confirmed_at").nullable()

        val account = reference("account", ExposedAccountRepository.Table, onDelete = ReferenceOption.CASCADE).uniqueIndex()
        val enabled = bool("enabled").default(false)

        val encryptedSecret = blob("encrypted_secret").nullable()

        val digits = integer("digits").nullable()
        val periodSeconds = long("period_seconds").nullable()
        val algorithm = varchar("algorithm", 10).nullable()
    }
}
