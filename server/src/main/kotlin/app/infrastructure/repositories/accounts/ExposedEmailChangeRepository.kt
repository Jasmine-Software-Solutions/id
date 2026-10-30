package app.infrastructure.repositories.accounts

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableInstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedEmailChange
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IEmailChangeRepository
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

class ExposedEmailChangeRepository(
    val hashFunction: IHashFunction,
    val accountRepository: IAccountRepository
) : ExposedIdentifiedEntityRepository<IHashedEmailChange, ExposedEmailChangeRepository.EmailChange>(Table),
    IEmailChangeRepository {

    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = EmailChange(row, insert, update)

    override fun transform(entity: IHashedEmailChange): EmailChange? = entity as? EmailChange

    override fun clone(src: EmailChange, dest: EmailChange) {
        runCatching {
            dest.verificationToken = src.verificationToken
        }
    }

    override fun findByAccount(id: UUID): List<IHashedEmailChange> = transaction {
        Table.select { Table.account eq id }
            .orderBy(Table.createdAt, SortOrder.DESC)
            .map { read(it, null, null) }
            .toList()
    }

    inner class EmailChange(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHashedEmailChange {
        private var _verificationToken: String? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)
        override var decidedAt: Instant? by nullableColumn(Table.decidedAt, NullableInstantTransformer)

        override var account: IAccount by column(Table.account,
            EntityTransformer(ExposedAccountRepository.Table, accountRepository))

        override var newEmail: String by column(Table.newEmail)
        override var confirmed: Boolean by column(Table.confirmed)

        override var verificationToken: String
            get() = _verificationToken ?: throw UnsupportedOperationException("IEmailChange#verificationToken is transient and no longer accessible")
            set(value) = hashFunction.hash(value.toByteArray()).let {
                row?.set(Table.verificationTokenHash, it)
                insert?.set(Table.verificationTokenHash, it)
                update?.set(Table.verificationTokenHash, it)
                _verificationToken = value
            }

        override fun verify(token: String): Boolean {
            return hashFunction.verify(token.toByteArray(), row!![Table.verificationTokenHash])
        }
    }

    object Table : UUIDTable("email_changes") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expires_at")

        val decidedAt = long("decided_at").nullable()
        val confirmed = bool("confirmed").default(false)

        val account = reference("account_id", ExposedAccountRepository.Table, onDelete = ReferenceOption.CASCADE)
        val newEmail = varchar("new_email", 320)

        val verificationTokenHash = text("argon2_verification_token_hash")
    }
}
