package app.infrastructure.repositories.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IHashedMagicLink
import app.domain.models.account.IMagicLink
import app.domain.repositories.IAccountRepository
import app.domain.repositories.IMagicLinkRepository
import app.domain.services.IMagicLinkService
import app.infrastructure.models.account.AccountsTable
import app.infrastructure.models.account.MagicLinksTable
import app.infrastructure.util.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ExposedMagicLinkRepository(
    val accountRepository: IAccountRepository,
    val magicLinkService: IMagicLinkService,
) : ExposedIdentifiedEntityRepository<IMagicLink, ExposedMagicLinkRepository.MagicLink>(MagicLinksTable, MagicLink::class), IMagicLinkRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = MagicLink(row, insert, update)

    override fun findByAccount(id: UUID): List<IMagicLink> = transaction {
        MagicLinksTable.select { MagicLinksTable.account eq id }
            .orderBy(MagicLinksTable.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    inner class MagicLink(
        row: ResultRow?,
        insert: InsertStatement<Number>?,
        update: UpdateStatement?
    ) : ExposedIdentifiedEntityWrapper(MagicLinksTable, row, insert, update), IHashedMagicLink {
        override val createdAt: Instant by column(MagicLinksTable.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(MagicLinksTable.expiresAt, InstantTransformer)
        override var decidedAt: Instant? by nullableColumn(MagicLinksTable.decidedAt, NullableInstantTransformer)

        override var account: IAccount by column(MagicLinksTable.account,
            EntityTransformer(AccountsTable, accountRepository))

        override var approved: Boolean by column(MagicLinksTable.approved)
        override var consumed: Boolean by column(MagicLinksTable.consumed)

        override var decisionToken: String by column(MagicLinksTable.decisionToken)
        override var acceptanceToken: String by column(MagicLinksTable.acceptanceTokenHash)

        override fun verifyDecisionToken(token: String): Boolean {
           return  magicLinkService.verify(token, decisionToken)
        }

        override fun verifyAcceptanceToken(token: String): Boolean {
            return magicLinkService.verify(token, acceptanceToken)
        }
    }
}
