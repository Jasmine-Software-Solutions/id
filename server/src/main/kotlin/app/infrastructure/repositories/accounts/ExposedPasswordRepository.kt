package app.infrastructure.repositories.accounts

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedPassword
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IPasswordRepository
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

class ExposedPasswordRepository(
    val hashFunction: IHashFunction,
    val accountRepository: IAccountRepository<IAccount>
) : ExposedIdentifiedEntityRepository<IHashedPassword, ExposedPasswordRepository.Password>(Table, Password::class),
    IPasswordRepository<IHashedPassword> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = Password(row, insert, update)

    override fun findByAccount(id: UUID): List<IHashedPassword> = transaction {
        Table.select { Table.account eq id }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    inner class Password(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHashedPassword {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var account: IAccount by column(Table.account,
            EntityTransformer(ExposedAccountRepository.Table, accountRepository))

        override var password: String
            get() = throw UnsupportedOperationException()
            set(value) = hashFunction.hash(value.toByteArray()).let {
                row?.set(Table.passwordHash, it)
                insert?.set(Table.passwordHash, it)
                update?.set(Table.passwordHash, it)
            }

        override fun verify(password: String): Boolean {
            return hashFunction.verify(password.toByteArray(), row!![Table.passwordHash])
        }
    }

    object Table : UUIDTable("passwords") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val account = reference("account_id", ExposedAccountRepository.Table, onDelete = ReferenceOption.CASCADE)
        val passwordHash = text("argon2_password_hash")
    }
}