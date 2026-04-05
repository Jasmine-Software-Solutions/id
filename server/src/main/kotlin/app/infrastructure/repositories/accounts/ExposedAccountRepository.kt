package app.infrastructure.repositories.accounts

import app.domain.models.account.IAccount
import app.domain.repositories.IAccountRepository
import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.InstantTransformer
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

open class ExposedAccountRepository
    : ExposedIdentifiedEntityRepository<IAccount, ExposedAccountRepository.Account>(Table, Account::class),
    IAccountRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = Account(row, insert, update)

    override fun findByEmail(email: String): IAccount? = transaction {
        val row = Table.select { Table.email eq email }.firstOrNull()
            ?: return@transaction null

        return@transaction read(row, null, null)
    }

    open class Account(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IAccount {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var email: String by column(Table.email)
        override var firstName: String by column(Table.firstName)
        override var lastName: String by column(Table.lastName)

        override var platformAdministrator: Boolean by column(Table.platformAdministrator)
    }

    object Table : UUIDTable("accounts") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val email = varchar("email", 320).uniqueIndex()

        val firstName = varchar("first_name", 40)
        val lastName = varchar("last_name", 40)

        val platformAdministrator = bool("platform_administrator").default(false)
    }
}
