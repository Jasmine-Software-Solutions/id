package app.infrastructure.repositories.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IEncryptedTOTPConfiguration
import app.domain.models.account.ITOTPConfiguration
import app.domain.repositories.IAccountRepository
import app.domain.repositories.ITOTPConfigurationRepository
import app.domain.services.ITOTPService
import app.infrastructure.models.account.AccountsTable
import app.infrastructure.models.account.TOTPConfigurationTable
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.ExposedColumnTransformer
import app.infrastructure.util.ExposedEntityWrapper
import app.infrastructure.util.NullableInstantTransformer
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.*

class ExposedTOTPConfigurationRepository(
    val accountRepository: IAccountRepository,
    val totpService: ITOTPService
) : ITOTPConfigurationRepository {
    override fun findByAccount(id: UUID): ITOTPConfiguration = transaction {
        val row = TOTPConfigurationTable.select { TOTPConfigurationTable.account eq id }.firstOrNull()
            ?: return@transaction create(id)

        if (row[TOTPConfigurationTable.enabled])
            return@transaction SetTOTPConfiguration(row, null, null)

        return@transaction TOTPConfiguration(row, null, null)
    }

    private fun create(id: UUID): ITOTPConfiguration = transaction {
        TOTPConfigurationTable.insert {
            it[TOTPConfigurationTable.account] = id
            it[TOTPConfigurationTable.createdAt] = System.currentTimeMillis()
            it[TOTPConfigurationTable.enabled] = false

            it[TOTPConfigurationTable.confirmedAt] = null
            it[TOTPConfigurationTable.digits] = null
            it[TOTPConfigurationTable.periodSeconds] = null
            it[TOTPConfigurationTable.algorithm] = null
            it[TOTPConfigurationTable.encryptedSecret] = null
        }

        return@transaction findByAccount(id)
    }

    override fun update(entity: ITOTPConfiguration, function: ITOTPConfiguration.() -> Unit) = transaction {
        val isExposedEntity = entity is TOTPConfiguration
        val oldWrappedEntity = if (isExposedEntity) entity as TOTPConfiguration else null

        TOTPConfigurationTable.update({ TOTPConfigurationTable.account eq entity.account.id }) {
            val row = oldWrappedEntity?.row

            val newWrappedEntity = SetTOTPConfiguration(row, null, it)
            function(newWrappedEntity)

            if (isExposedEntity) {
                oldWrappedEntity?.row = row
                oldWrappedEntity?.insert = null
                oldWrappedEntity?.update = null
            }

            if (!entity.enabled) {
                it[TOTPConfigurationTable.confirmedAt] = null
                it[TOTPConfigurationTable.digits] = null
                it[TOTPConfigurationTable.periodSeconds] = null
                it[TOTPConfigurationTable.algorithm] = null
                it[TOTPConfigurationTable.encryptedSecret] = null
            }
        }

        Unit
    }

    open inner class TOTPConfiguration(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedEntityWrapper(row, insert, update), ITOTPConfiguration {
        override var account: IAccount by column(TOTPConfigurationTable.account,
            EntityTransformer(AccountsTable, accountRepository))

        override var enabled: Boolean by column(TOTPConfigurationTable.enabled)
    }

    inner class SetTOTPConfiguration(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : TOTPConfiguration(row, insert, update), IEncryptedTOTPConfiguration {
        override var confirmedAt: Instant? by nullableColumn(TOTPConfigurationTable.confirmedAt, NullableInstantTransformer)

        override var digits: Int by requiredColumn(TOTPConfigurationTable.digits)
        override var periodSeconds: Long by requiredColumn(TOTPConfigurationTable.periodSeconds)
        override var algorithm: String by requiredColumn(TOTPConfigurationTable.algorithm)

        override var secret: ByteArray by requiredColumn(TOTPConfigurationTable.encryptedSecret,
            transformer = ExposedColumnTransformer(
                fromColumn = { it!!.bytes },
                toColumn = { ExposedBlob(it) }
            ))

        override fun verify(code: Int): Boolean {
            return totpService.verify(secret, code)
        }
    }
}
