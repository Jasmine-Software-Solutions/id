package app.infrastructure.repositories.clients

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedSessionRepository
import app.infrastructure.util.*
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRegisteredScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.client.IHashedDelegatedSessionCode
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.net.URI
import java.time.Instant
import java.util.*

class ExposedDelegatedSessionRepository(
    val hashFunction: IHashFunction,
    val sessionRepository: ISessionRepository,
    val clientRepository: IClientRepository,
    val scopeRepository: IScopeRepository,
    val scopeRegistry: IScopeRegistry<out IScope>
) : ExposedIdentifiedEntityRepository<IDelegatedSession, ExposedDelegatedSessionRepository.DelegatedSession>(Table),
    IDelegatedSessionRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = DelegatedSession(row, insert, update)

    override fun transform(entity: IDelegatedSession): DelegatedSession? = entity as? DelegatedSession

    override fun clone(src: DelegatedSession, dest: DelegatedSession) {
        runCatching {
            dest.code = src.code
        }
    }

    open inner class DelegatedSession(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IDelegatedSession {
        private var _code: String? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var refreshedAt: Instant? by nullableColumn(Table.refreshedAt, NullableInstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)

        override var session: IHashedSession by column(Table.session,
            EntityTransformer(ExposedSessionRepository.Table, sessionRepository))

        override var client: IClient by column(Table.client,
            EntityTransformer(ExposedSessionRepository.Table, clientRepository))

        override var redirectUri: URI by column(Table.redirectUri, transformer = ExposedColumnTransformer(
            fromColumn = { URI.create(it) },
            toColumn = { it.toString() }))

        override var nonce: String? by nullableColumn(Table.nonce)

        override var handoffId: UUID? by nullableColumn(Table.handoffId)

        override var scopes: Set<IScope> by column(Table.scope, ExposedColumnTransformer(
            fromColumn = { it.split(" ").mapNotNull {
                scopeRegistry.findById(it) ?: scopeRepository.findById(it)
            }.toSet() },
            toColumn = { it.map { it.id }.joinToString(" ") }
        ))

        override var code = object : IHashedDelegatedSessionCode {
            override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
            override var expiresAt: Instant by column(Table.codeExpiresAt, InstantTransformer)

            override var code: String
                get() = _code ?: throw UnsupportedOperationException("IDelegatedSession#code is transient and no longer accessible")
                set(value) = hashFunction.hash(value.toByteArray()).let {
                    row?.set(Table.codeHash, it)
                    insert?.set(Table.codeHash, it)
                    update?.set(Table.codeHash, it)
                    _code = value
                }

            override fun verify(code: String): Boolean {
                return hashFunction.verify(code.toByteArray(), row!![Table.codeHash])
            }
        }
    }

    object Table : UUIDTable("delegated_sessions") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val refreshedAt = long("refreshed_at").nullable()
        val expiresAt = long("expires_at")

        val session = reference("session", ExposedSessionRepository.Table, onDelete = ReferenceOption.CASCADE)
        val client = reference("client", ExposedClientRepository.Table, onDelete = ReferenceOption.CASCADE)

        val redirectUri = varchar("redirect_uri", 2048)
        val scope = varchar("scope", 256)
        val nonce = varchar("nonce", 256).nullable()

        /**
         * The handoff this code was issued for, if any. Deliberately a plain
         * UUID with no foreign key: the handoff may be deleted before the code
         * is exchanged, and a missing row must fail the exchange closed rather
         * than turn a handoff code into a normal one.
         */
        val handoffId = uuid("handoff_id").nullable()

        val codeHash = text("argon2_code_hash")
        val codeExpiresAt = long("code_expires_at")

        init {
            index(false, handoffId)
        }
    }
}
