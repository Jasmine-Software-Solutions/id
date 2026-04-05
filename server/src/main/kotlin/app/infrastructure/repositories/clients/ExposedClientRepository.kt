package app.infrastructure.repositories.clients

import app.domain.models.client.IClient
import app.domain.models.client.IClientRedirectUris
import app.domain.models.client.IHashedClient
import app.domain.repositories.IClientRepository
import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.InstantTransformer
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.time.Instant
import java.util.*

class ExposedClientRepository
    : ExposedIdentifiedEntityRepository<IClient, ExposedClientRepository.Client>(Table, Client::class),
    IClientRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = Client(row, insert, update)

    override fun createDependents(id: UUID, entity: IClient) {
        for (uri in entity.redirectUris)
            addRedirectUri(id, uri)
    }

    private fun findRedirectUris(id: UUID): List<URI> = transaction {
        val uris = RedirectUrisTable.select { RedirectUrisTable.client eq id }
            .map { it[RedirectUrisTable.uri] }
            .map { URI.create(it) }
            .toList()

        return@transaction uris
    }

    private fun addRedirectUri(id: UUID, uri: URI) {
        RedirectUrisTable.insert {
            it[RedirectUrisTable.client] = id
            it[RedirectUrisTable.uri] = uri.toString()
        }
    }

    private fun removeRedirectUri(id: UUID, uri: URI) {
        RedirectUrisTable.deleteWhere { RedirectUrisTable.client eq id and (RedirectUrisTable.uri eq uri.toString()) }
    }

    open inner class Client(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHashedClient {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var name: String by column(Table.name)
        override var suspended by column(Table.suspended)
        override var confidential by column(Table.confidential)

        override val redirectUris = object : IClientRedirectUris {
            override val values: List<URI> = findRedirectUris(id).toMutableList()

            override fun add(uri: URI) {
                if (insert == null) try {
                    addRedirectUri(id, uri)
                } catch (e: IllegalStateException) {
                    throw IllegalStateException("Invoked IClient#add(URI) outside of IRepository#update(IClient, ...) callback")
                }

                (values as MutableList).add(uri)
            }

            override fun remove(uri: URI) {
                if (insert == null) try {
                    removeRedirectUri(id, uri)
                } catch (e: IllegalStateException) {
                    throw IllegalStateException("Invoked IClient#remove(URI) outside of IRepository#update(IClient, ...) callback")
                }

                (values as MutableList).remove(uri)
            }

            override fun iterator(): Iterator<URI> {
                return values.iterator()
            }
        }

        override var secret: String by column(Table.secret)

        override fun isSecret(secret: String): Boolean {
            TODO("Not yet implemented")
        }
    }

    object Table : UUIDTable("clients") {
        val createdAt = long("created_at")
        val suspended = bool("suspended").default(false)

        val name = varchar("name", 64)
        val confidential = bool("confidential")

        val secret = varchar("secret", 64)

        val automaticGrant = bool("automatic_grant").default(false)

        val scope = text("scope").nullable()
    }
    
    object RedirectUrisTable : UUIDTable("client_redirect_uris") {
        val client = reference("client_id", Table, onDelete = ReferenceOption.CASCADE)
        val uri = varchar("uri", 2048)
    }
}