package app.infrastructure.repositories.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IHashedSession
import app.domain.models.account.ISession
import app.domain.repositories.IAccountRepository
import app.domain.repositories.ISessionRepository
import app.infrastructure.models.account.AccountsTable
import app.infrastructure.models.account.SessionsTable
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

class ExposedSessionRepository(
    val accountRepository: IAccountRepository
) : ExposedIdentifiedEntityRepository<ISession, ExposedSessionRepository.Session>(SessionsTable, Session::class), ISessionRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = Session(row, insert, update)

    override fun findByAccount(id: UUID): List<ISession> = transaction {
        SessionsTable.select { SessionsTable.account eq id }
            .orderBy(SessionsTable.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    open inner class Session(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntityWrapper(SessionsTable, row, insert, update), IHashedSession {
        override val createdAt: Instant by column(SessionsTable.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(SessionsTable.expiresAt, InstantTransformer)

        override var userAgent: String by column(SessionsTable.userAgent)
        override var ipAddress: String by column(SessionsTable.ipAddress)

        override var account: IAccount by column(SessionsTable.account,
            EntityTransformer(AccountsTable, accountRepository))

        override var token: String by column(SessionsTable.token)

        override fun verify(token: String): Boolean {
            TODO("Not yet implemented")
        }
    }
}
