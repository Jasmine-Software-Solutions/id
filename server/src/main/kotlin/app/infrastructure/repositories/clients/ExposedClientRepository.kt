package app.infrastructure.repositories.clients

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IClientRedirectUris
import com.jasminesoftwaresolutions.id.domain.models.client.IHashedClient
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.time.Instant
import java.util.*

class ExposedClientRepository(
    val hashFunction: IHashFunction
) : ExposedIdentifiedEntityRepository<IClient, ExposedClientRepository.Client>(Table, Client::class),
    IClientRepository<IClient> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = Client(row, insert, update)

    override fun clone(src: Client, dest: Client) {
        (dest.redirectUris.values as MutableList).clear()
        (dest.redirectUris.values as MutableList).addAll(src.redirectUris.values)

        runCatching {
            dest.secret = src.secret
        }
    }

    override fun createDependents(id: UUID, entity: IClient) {
        for (uri in entity.redirectUris.values)
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
        private var _secret: String? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var name: String by column(Table.name)
        override var suspended by column(Table.suspended)
        override var confidential by column(Table.confidential)

        override val redirectUris = object : IClientRedirectUris {
            override val values: List<URI> =
                if (insert == null) findRedirectUris(id).toMutableList()
                else mutableListOf()

            override fun add(uri: URI) {
                if (insert == null) try {
                    addRedirectUri(id, uri)
                } catch (e: IllegalStateException) {
                    throw IllegalStateException("Invoked IClientRedirectUris#add(URI) outside of IRepository#update(IClient, ...) callback")
                }

                (values as MutableList).add(uri)
            }

            override fun remove(uri: URI) {
                if (insert == null) try {
                    removeRedirectUri(id, uri)
                } catch (e: IllegalStateException) {
                    throw IllegalStateException("Invoked IClientRedirectUris#remove(URI) outside of IRepository#update(IClient, ...) callback")
                }

                (values as MutableList).remove(uri)
            }
        }

        override var secret: String
            get() = _secret ?: throw UnsupportedOperationException("IClient#secret is transient and no longer accessible")
            set(value) = hashFunction.hash(value.toByteArray()).let {
                row?.set(Table.secretHash, it)
                insert?.set(Table.secretHash, it)
                update?.set(Table.secretHash, it)
                _secret = value
            }

        override fun verify(secret: String): Boolean {
            return hashFunction.verify(secret.toByteArray(), row!![Table.secretHash])
        }
    }

    object Table : UUIDTable("clients") {
        val createdAt = long("created_at")
        val suspended = bool("suspended").default(false)

        val name = varchar("name", 64)
        val confidential = bool("confidential")

        val secretHash = varchar("argon2_secret_hash", 64)

        val automaticGrant = bool("automatic_grant").default(false)

        val scope = text("scope").nullable()
    }
    
    object RedirectUrisTable : UUIDTable("client_redirect_uris") {
        val client = reference("client_id", Table, onDelete = ReferenceOption.CASCADE)
        val uri = varchar("uri", 2048)
    }
}