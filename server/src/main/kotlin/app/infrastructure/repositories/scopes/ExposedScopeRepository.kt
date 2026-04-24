package app.infrastructure.repositories.scopes

import app.infrastructure.repositories.clients.ExposedClientRepository
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRegisteredScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IScopeRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class ExposedScopeRepository(
    private val clientRepository: IClientRepository<out IClient>
) : IScopeRepository<IRegisteredScope> {
    private val scopeIdPattern = Regex("^[\\u0021\\u0023-\\u005B\\u005D-\\u007E]+$")

    private fun requireValidId(id: String) {
        if (!scopeIdPattern.matches(id)) {
            throw IllegalArgumentException("Scope id must match OAuth2 scope-token format")
        }
    }

    private fun requireClient(scope: IRegisteredScope): IClient {
        return scope.client
            ?: throw IllegalArgumentException("Registered scope must be associated to a client")
    }

    private fun toScope(row: ResultRow): IRegisteredScope = RegisteredScope(
        client = clientRepository.findById(row[Table.client].value),
        id = row[Table.id],
        description = row[Table.description],
        isByTenant = row[Table.isByTenant]
    )

    override fun entries(): Set<IRegisteredScope> = transaction {
        Table.selectAll().map(::toScope).toSet()
    }

    override fun findById(id: String): IRegisteredScope? = transaction {
        val row = Table.select { Table.id eq id }.firstOrNull()
            ?: return@transaction null

        return@transaction toScope(row)
    }

    override fun findByClient(id: UUID): List<IRegisteredScope> = transaction {
        Table.select { Table.client eq id }
            .map(::toScope)
            .toList()
    }

    override fun create(function: IRegisteredScope.() -> Unit): IRegisteredScope = transaction {
        val scope = RegisteredScope(client = null, id = "", description = "", isByTenant = false)
        scope.function()

        requireValidId(scope.id)
        val client = requireClient(scope)

        Table.insert {
            it[Table.id] = scope.id
            it[Table.description] = scope.description
            it[Table.client] = client.id
        }

        return@transaction scope
    }

    override fun update(entity: IRegisteredScope, function: IRegisteredScope.() -> Unit) = transaction {
        val originalId = entity.id

        val updated = RegisteredScope(
            client = entity.client,
            id = entity.id,
            description = entity.description,
            isByTenant = entity.isByTenant
        )
        updated.function()

        requireValidId(updated.id)
        val client = requireClient(updated)

        Table.update({ Table.id eq originalId }) {
            it[Table.id] = updated.id
            it[Table.description] = updated.description
            it[Table.client] = client.id
            it[Table.isByTenant] = updated.isByTenant
        }

        entity.id = updated.id
        entity.description = updated.description
        entity.client = updated.client
        entity.isByTenant = updated.isByTenant
    }

    override fun delete(entity: IRegisteredScope) = transaction {
        Table.deleteWhere { Table.id eq entity.id }
        Unit
    }

    override fun install() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Table
            )
        }
    }

    init {
        install()
    }

    private data class RegisteredScope(
        override var client: IClient?,
        override var id: String,
        override var description: String,
        override var isByTenant: Boolean,
    ) : IRegisteredScope

    object Table : org.jetbrains.exposed.sql.Table("scopes") {
        val client = reference("client", ExposedClientRepository.Table, onDelete = ReferenceOption.CASCADE)

        val id = varchar("id", 128)
        val description = varchar("description", 1024)
        val isByTenant = bool("is_by_tenant").default(false)

        override val primaryKey = PrimaryKey(id)
    }
}
