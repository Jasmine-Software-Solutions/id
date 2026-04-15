package app.infrastructure.repositories.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IHashedMagicLink
import app.domain.models.account.IMagicLink
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
    val hashService: IHashFunction,
    val accountRepository: IAccountRepository
) : ExposedIdentifiedEntityRepository<IMagicLink, ExposedMagicLinkRepository.MagicLink>(Table, MagicLink::class), IMagicLinkRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = MagicLink(row, insert, update)

    override fun findByAccount(id: UUID): List<IMagicLink> = transaction {
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
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)
        override var decidedAt: Instant? by nullableColumn(Table.decidedAt, NullableInstantTransformer)

        override var account: IAccount by column(Table.account,
            EntityTransformer(AccountsTable, accountRepository))

        override var approved: Boolean by column(Table.approved)
        override var consumed: Boolean by column(Table.consumed)

        override var decisionToken: String
            get() = throw UnsupportedOperationException()
            set(value) = hashService.hash(value.toByteArray()).let {
                row?.set(Table.decisionTokenHash, it)
                insert?.set(Table.decisionTokenHash, it)
                update?.set(Table.decisionTokenHash, it)
            }

        override var acceptanceToken: String
            get() = throw UnsupportedOperationException()
            set(value) = hashService.hash(value.toByteArray()).let {
                row?.set(Table.acceptanceTokenHash, it)
                insert?.set(Table.acceptanceTokenHash, it)
                update?.set(Table.acceptanceTokenHash, it)
            }

        override fun verifyDecisionToken(token: String): Boolean {
            return hashService.verify(token.toByteArray(), row!![Table.decisionTokenHash])
        }

        override fun verifyAcceptanceToken(token: String): Boolean {
            return hashService.verify(token.toByteArray(), row!![Table.acceptanceTokenHash])
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
