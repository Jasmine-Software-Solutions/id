package app.infrastructure.repositories.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IHashedSession
import app.domain.models.account.ISession
import app.domain.repositories.IAccountRepository
import app.domain.repositories.ISessionRepository
import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.models.account.AccountsTable
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
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
) : ExposedIdentifiedEntityRepository<ISession, ExposedSessionRepository.Session>(Table, Session::class),
    ISessionRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = Session(row, insert, update)

    override fun findByAccount(id: UUID): List<ISession> = transaction {
        Table.select { Table.account eq id }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    open inner class Session(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHashedSession {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)

        override var userAgent: String by column(Table.userAgent)
        override var ipAddress: String by column(Table.ipAddress)

        override var account: IAccount by column(Table.account,
            EntityTransformer(ExposedAccountRepository.Table, accountRepository))

        override var token: String by column(Table.token)

        override fun verify(token: String): Boolean {
            TODO("Not yet implemented")
        }
    }

    object Table : UUIDTable("sessions") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expires_at")

        val account = reference("account", AccountsTable, onDelete = ReferenceOption.CASCADE)

        val userAgent = varchar("user_agent", 256)
        val ipAddress = varchar("ip_address", 39)

        val token = varchar("token", 32).uniqueIndex()
    }
}
