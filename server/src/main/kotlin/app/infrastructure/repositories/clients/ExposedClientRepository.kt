package app.infrastructure.repositories.clients

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.ExposedColumnTransformer
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
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.net.URI
import java.time.Instant
import java.util.*

/** The client secret digest column, and the name it is being renamed from. */
private const val SECRET_HASH_COLUMN = "secret_hash"
private const val LEGACY_SECRET_HASH_COLUMN = "argon2_secret_hash"

class ExposedClientRepository(
    val hashFunction: IHashFunction
) : ExposedIdentifiedEntityRepository<IClient, ExposedClientRepository.Client>(Table),
    IClientRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?) =
        Client(row, insert, update)

    override fun transform(entity: IClient): Client? = entity as? Client

    override fun entries(): List<IClient> = transaction {
        Table.select { org.jetbrains.exposed.sql.Op.TRUE }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    override fun findByFqdn(fqdn: String): IClient? = transaction {
        val row = Table.select { Table.fqdn eq fqdn }.firstOrNull()
            ?: return@transaction null
        return@transaction read(row, null, null)
    }

    /** Override create to derive client UUID deterministically from the FQDN. */
    override fun create(function: IClient.() -> Unit): IClient {
        // Capture the FQDN via a lightweight proxy before generating the UUID.
        val capture = FqdnCapture()
        function(capture)
        val fqdn = capture.fqdn
        if (fqdn.isBlank()) throw IllegalArgumentException("Client fqdn is required")
        val uuid = UUID.nameUUIDFromBytes(fqdn.toByteArray())
        return create(uuid, function)
    }

    /** Lightweight IClient for capturing the FQDN before UUID generation. */
    private class FqdnCapture : IClient {
        override var fqdn: String = ""
        override var suspended: Boolean = false
        override var confidential: Boolean = false
        override var secret: String = ""
        override var name: String? = null
        override var logoUri: URI? = null
        override var initiateLoginUri: URI? = null
        override val id: UUID get() = UUID(0, 0)
        override val createdAt: Instant get() = Instant.EPOCH
        override val redirectUris: IClientRedirectUris = object : IClientRedirectUris {
            override val values: List<URI> = mutableListOf()
            override fun add(uri: URI) {
                (values as MutableList).add(uri)
            }

            override fun remove(uri: URI) {}
        }

        override fun verify(secret: String): Boolean = false
    }

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

    override fun install() {
        transaction {
            migrateLegacySecretHashColumn()
            SchemaUtils.createMissingTablesAndColumns(
                Table,
                RedirectUrisTable
            )
        }
    }

    /**
     * Renames the secret digest column from `argon2_secret_hash`, a name that
     * describes an algorithm this table never used: client secrets are hashed
     * with `SHA256HashFunction` — the `hashFunction` this repository is built
     * with — while Argon2 hashes passwords. A schema claiming the wrong
     * primitive is an audit hazard, so the column is renamed to match what it
     * actually holds.
     *
     * The rename has to run *before* [SchemaUtils.createMissingTablesAndColumns].
     * On a database created under the old name, actualization would otherwise
     * see `secret_hash` missing and add it empty beside the populated legacy
     * column, after which `verify` reads null for every existing confidential
     * client. Renaming first leaves actualization nothing to add.
     *
     * The copy branch is only reachable if a database was started once with
     * the renamed column before this migration existed; the digests are then
     * still in the legacy column, so they are carried across. The `IS NULL`
     * guard keeps it a no-op on rows that already hold a digest.
     */
    private fun Transaction.migrateLegacySecretHashColumn() {
        val columns = currentDialect.tableColumns(Table)[Table]
            .orEmpty()
            .map { it.name.lowercase() }
        if (LEGACY_SECRET_HASH_COLUMN !in columns) return

        if (SECRET_HASH_COLUMN in columns) {
            exec(
                "UPDATE ${Table.tableName}" +
                    " SET $SECRET_HASH_COLUMN = $LEGACY_SECRET_HASH_COLUMN" +
                    " WHERE $SECRET_HASH_COLUMN IS NULL"
            )
        } else {
            exec(
                "ALTER TABLE ${Table.tableName}" +
                    " RENAME COLUMN $LEGACY_SECRET_HASH_COLUMN TO $SECRET_HASH_COLUMN"
            )
        }
    }

    open inner class Client(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHashedClient {
        private var _secret: String? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var fqdn: String by column(Table.fqdn)
        override var suspended by column(Table.suspended)
        override var confidential by column(Table.confidential)

        override var name: String? by column(Table.name)

        override var logoUri: URI? by nullableColumn(
            Table.logoUri,
            NullableUriTransformer,
        )

        override var initiateLoginUri: URI? by nullableColumn(
            Table.initiateLoginUri,
            NullableUriTransformer,
        )

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
            get() = _secret
                ?: throw UnsupportedOperationException("IClient#secret is transient and no longer accessible")
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
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val suspended = bool("suspended").default(false)

        val fqdn = varchar("fqdn", 253).uniqueIndex()
        val confidential = bool("confidential")

        val name = varchar("display_name", 255).nullable()
        val logoUri = varchar("logo_uri", 2048).nullable()
        val initiateLoginUri = varchar("initiate_login_uri", 2048).nullable()

        val secretHash = text(SECRET_HASH_COLUMN)
    }

    object RedirectUrisTable : UUIDTable("client_redirect_uris") {
        val client = reference("client_id", Table, onDelete = ReferenceOption.CASCADE)
        val uri = varchar("uri", 2048)
    }

    companion object {
        val NullableUriTransformer = ExposedColumnTransformer<String?, URI?>(
            fromColumn = { it?.let { URI.create(it) } },
            toColumn = { it?.toString() },
        )
    }
}
