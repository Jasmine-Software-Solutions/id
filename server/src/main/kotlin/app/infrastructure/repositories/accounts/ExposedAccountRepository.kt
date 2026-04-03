package app.infrastructure.repositories.accounts

import app.domain.models.account.IAccount
import app.domain.repositories.IAccountRepository
import app.infrastructure.models.account.AccountsTable
import app.infrastructure.util.ExposedIdentifiedEntityRepository
import app.infrastructure.util.ExposedIdentifiedEntityWrapper
import app.infrastructure.util.InstantTransformer
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

open class ExposedAccountRepository : ExposedIdentifiedEntityRepository<IAccount, ExposedAccountRepository.Account>(AccountsTable, Account::class), IAccountRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = Account(row, insert, update)

    override fun findByEmail(email: String): IAccount? = transaction {
        val row = AccountsTable.select { AccountsTable.email eq email }.firstOrNull()
            ?: return@transaction null

        return@transaction read(row, null, null)
    }

    open class Account(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntityWrapper(AccountsTable, row, insert, update), IAccount {
        override val createdAt: Instant by column(AccountsTable.createdAt, InstantTransformer)

        override var email: String by column(AccountsTable.email)
        override var firstName: String by column(AccountsTable.firstName)
        override var lastName: String by column(AccountsTable.lastName)

        override var platformAdministrator: Boolean by column(AccountsTable.systemAdmin)
    }
}
