package app.infrastructure.repositories.accounts

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ISessionRepository
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
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
    val hashFunction: IHashFunction,
    val accountRepository: IAccountRepository<IAccount>
) : ExposedIdentifiedEntityRepository<IHashedSession, ExposedSessionRepository.Session>(Table, Session::class),
    ISessionRepository<IHashedSession> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = Session(row, insert, update)

    override fun clone(src: Session, dest: Session) {
        runCatching {
            dest.token = src.token
        }
    }

    override fun findByAccount(id: UUID): List<IHashedSession> = transaction {
        Table.select { Table.account eq id }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    inner class Session(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHashedSession {
        private var _token: String? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)

        override var userAgent: String? by nullableColumn(Table.userAgent)
        override var ipAddress: String? by nullableColumn(Table.ipAddress)

        override var account: IAccount by column(Table.account,
            EntityTransformer(ExposedAccountRepository.Table, accountRepository))

        override var token: String
            get() = _token ?: throw UnsupportedOperationException("ISession#token is transient and no longer accessible")
            set(value) = hashFunction.hash(value.toByteArray()).let {
                row?.set(Table.tokenHash, it)
                insert?.set(Table.tokenHash, it)
                update?.set(Table.tokenHash, it)
                _token = value
            }

        override fun verify(token: String): Boolean {
            return hashFunction.verify(token.toByteArray(), row!![Table.tokenHash])
        }
    }

    object Table : UUIDTable("sessions") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expires_at")

        val account = reference("account", ExposedAccountRepository.Table, onDelete = ReferenceOption.CASCADE)

        val userAgent = varchar("user_agent", 256).nullable()
        val ipAddress = varchar("ip_address", 39).nullable()

        val tokenHash = text("argon2_token_hash")
    }
}
