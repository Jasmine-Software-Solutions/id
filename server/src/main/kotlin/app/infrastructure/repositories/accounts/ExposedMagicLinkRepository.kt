package app.infrastructure.repositories.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IHashedMagicLink
import app.domain.repositories.IAccountRepository
import app.domain.repositories.IMagicLinkRepository
import app.domain.services.IHashFunction
import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.models.account.AccountsTable
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableInstantTransformer
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

class ExposedMagicLinkRepository(
    val hashFunction: IHashFunction,
    val accountRepository: IAccountRepository<IAccount>
) : ExposedIdentifiedEntityRepository<IHashedMagicLink, ExposedMagicLinkRepository.MagicLink>(Table, MagicLink::class), IMagicLinkRepository<IHashedMagicLink> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = MagicLink(row, insert, update)

    override fun clone(src: MagicLink, dest: MagicLink) {
        runCatching {
            dest.acceptanceToken = src.acceptanceToken
        }

        runCatching {
            dest.decisionToken = src.decisionToken
        }
    }

    override fun findByAccount(id: UUID): List<IHashedMagicLink> = transaction {
        Table.select { Table.account eq id }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    inner class MagicLink(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHashedMagicLink {
        private var _decisionToken: String? = null
        private var _acceptanceToken: String? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)
        override var decidedAt: Instant? by nullableColumn(Table.decidedAt, NullableInstantTransformer)

        override var account: IAccount by column(Table.account,
            EntityTransformer(AccountsTable, accountRepository))

        override var approved: Boolean by column(Table.approved)
        override var consumed: Boolean by column(Table.consumed)

        override var decisionToken: String
            get() = _decisionToken ?: throw UnsupportedOperationException("IMagicLink#decisionToken is transient and no longer accessible")
            set(value) = hashFunction.hash(value.toByteArray()).let {
                row?.set(Table.decisionTokenHash, it)
                insert?.set(Table.decisionTokenHash, it)
                update?.set(Table.decisionTokenHash, it)
                _decisionToken = value
            }

        override var acceptanceToken: String
            get() = _acceptanceToken ?: throw UnsupportedOperationException("IMagicLink#acceptanceToken is transient and no longer accessible")
            set(value) = hashFunction.hash(value.toByteArray()).let {
                row?.set(Table.acceptanceTokenHash, it)
                insert?.set(Table.acceptanceTokenHash, it)
                update?.set(Table.acceptanceTokenHash, it)
                _acceptanceToken = value
            }

        override fun verifyDecisionToken(token: String): Boolean {
            return hashFunction.verify(token.toByteArray(), row!![Table.decisionTokenHash])
        }

        override fun verifyAcceptanceToken(token: String): Boolean {
            return hashFunction.verify(token.toByteArray(), row!![Table.acceptanceTokenHash])
        }
    }

    object Table : UUIDTable("magic_links") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expires_at")

        var decidedAt = long("decided_at").nullable()
        var approved = bool("approved").default(false)
        var consumed = bool("consumed").default(false)

        val account = reference("account_id", AccountsTable, onDelete = ReferenceOption.CASCADE)

        val decisionTokenHash = text("argon2_decision_token_hash")
        val acceptanceTokenHash = text("argon2_acceptance_token_hash")
    }
}
