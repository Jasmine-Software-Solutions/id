package app.infrastructure.repositories.accounts

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.ExposedColumnTransformer
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.IPlatformRole
import com.jasminesoftwaresolutions.id.domain.models.authorization.PlatformAdministrator
import com.jasminesoftwaresolutions.id.domain.models.authorization.PlatformMember
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

open class ExposedAccountRepository
    : ExposedIdentifiedEntityRepository<IAccount, ExposedAccountRepository.Account>(Table, Account::class),
    IAccountRepository<IAccount> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = Account(row, insert, update)

    override fun findByEmail(email: String): IAccount? = transaction {
        val row = Table.select { Table.email eq email }.firstOrNull()
            ?: return@transaction null

        return@transaction read(row, null, null)
    }

    open class Account(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IAccount {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var email: String by column(Table.email)
        override var firstName: String by column(Table.firstName)
        override var lastName: String by column(Table.lastName)

        override val roles: Set<IPlatformRole> by column(Table.roles, ExposedColumnTransformer(
            fromColumn = { it.split(",").mapNotNull {
                if (it == PlatformAdministrator.id)
                    PlatformAdministrator
                else if (it == PlatformMember.id)
                    PlatformMember
                else null
            }.toSet() },
            toColumn = { it.map { it.id }.joinToString(",") },
        ))
    }

    object Table : UUIDTable("accounts") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val email = varchar("email", 320).uniqueIndex()

        val firstName = varchar("first_name", 40)
        val lastName = varchar("last_name", 40)

        val roles = text("roles").clientDefault { PlatformMember.id }
    }
}
