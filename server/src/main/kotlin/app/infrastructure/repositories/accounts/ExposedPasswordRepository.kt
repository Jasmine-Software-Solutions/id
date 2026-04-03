package app.infrastructure.repositories.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IHashedPassword
import app.domain.models.account.IPassword
import app.domain.repositories.IAccountRepository
import app.domain.repositories.IPasswordRepository
import app.domain.services.IPasswordService
import app.infrastructure.models.account.AccountsTable
import app.infrastructure.models.account.PasswordsTable
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.ExposedIdentifiedEntityRepository
import app.infrastructure.util.ExposedIdentifiedEntityWrapper
import app.infrastructure.util.InstantTransformer
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ExposedPasswordRepository(
    val accountRepository: IAccountRepository,
    val passwordService: IPasswordService
) : ExposedIdentifiedEntityRepository<IPassword, ExposedPasswordRepository.Password>(PasswordsTable, Password::class), IPasswordRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = Password(row, insert, update)

    override fun findByAccount(id: UUID): List<IPassword> = transaction {
        PasswordsTable.select { PasswordsTable.account eq id }
            .orderBy(PasswordsTable.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    inner class Password(
        row: ResultRow?,
        insert: InsertStatement<Number>?,
        update: UpdateStatement?
    ) : ExposedIdentifiedEntityWrapper(PasswordsTable, row, insert, update), IHashedPassword {
        override val createdAt: Instant by column(PasswordsTable.createdAt, InstantTransformer)

        override var account: IAccount by column(PasswordsTable.account,
            EntityTransformer(AccountsTable, accountRepository))

        override var password: String by column(PasswordsTable.passwordHash)

        override fun verify(password: String): Boolean {
            return passwordService.verify(this.password, password)
        }
    }
}