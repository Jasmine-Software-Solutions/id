package app.infrastructure.repositories.accounts

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.ExposedColumnTransformer
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

open class ExposedAccountRepository :
    ExposedIdentifiedEntityRepository<IAccount, ExposedAccountRepository.Account>(Table),
    IAccountRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?) =
        Account(row, insert, update)

    override fun transform(entity: IAccount): Account? = entity as? Account

    override fun findByEmail(email: String): IAccount? = transaction {
        val row = Table.select { Table.email eq email }.firstOrNull()
            ?: return@transaction null

        return@transaction read(row, null, null)
    }

    override fun entries(): List<IAccount> = transaction {
        Table.select { org.jetbrains.exposed.sql.Op.TRUE }
            .orderBy(Table.createdAt, org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    override fun install() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Table)
        }
    }

    open inner class Account(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IAccount {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var email: String by column(Table.email)
        override var firstName: String by column(Table.firstName)
        override var lastName: String by column(Table.lastName)
        override var emailVerified: Boolean by column(Table.emailVerified)

        override var ssoFavoriteClientIds: List<String> by column(
            Table.ssoFavoriteClientIds,
            JsonListTransformer,
        )

        override var ssoClientOrder: List<String> by column(
            Table.ssoClientOrder,
            JsonListTransformer,
        )
    }

    object Table : UUIDTable("accounts") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val email = varchar("email", 320).uniqueIndex()

        val firstName = varchar("first_name", 40)
        val lastName = varchar("last_name", 40)

        val emailVerified = bool("email_verified").clientDefault { false }

        val ssoFavoriteClientIds = text("sso_favorite_client_ids").default("[]")
        val ssoClientOrder = text("sso_client_order").default("[]")
    }

    companion object {
        private fun parseJsonList(raw: String): List<String> {
            if (raw.isBlank() || raw == "[]") return emptyList()
            return raw.removeSurrounding("[", "]").split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        }

        private fun toJsonList(list: List<String>): String {
            if (list.isEmpty()) return "[]"
            return list.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
        }

        val JsonListTransformer = ExposedColumnTransformer<String, List<String>>(
            fromColumn = ::parseJsonList,
            toColumn = ::toJsonList,
        )
    }
}
