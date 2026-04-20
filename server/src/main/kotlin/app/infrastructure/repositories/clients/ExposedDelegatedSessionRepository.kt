package app.infrastructure.repositories.clients

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedSessionRepository
import app.infrastructure.repositories.tenants.ExposedTenantRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.ExposedColumnTransformer
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableEntityTransformer
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IHashedDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IDelegatedSessionRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ISessionRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.net.URI
import java.time.Instant

class ExposedDelegatedSessionRepository(
    val hashFunction: IHashFunction,
    val sessionRepository: ISessionRepository<out ISession>,
    val clientRepository: IClientRepository<out IClient>,
    val tenantRepository: ITenantRepository<out ITenant>
) : ExposedIdentifiedEntityRepository<IHashedDelegatedSession, ExposedDelegatedSessionRepository.DelegatedSession>(Table, DelegatedSession::class),
    IDelegatedSessionRepository<IHashedDelegatedSession> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = DelegatedSession(row, insert, update)

    override fun clone(src: DelegatedSession, dest: DelegatedSession) {
        runCatching {
            dest.code = src.code
        }
    }

    open inner class DelegatedSession(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IHashedDelegatedSession {
        private var _code: String? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)

        override var session: ISession by column(Table.session,
            EntityTransformer(ExposedSessionRepository.Table, sessionRepository))

        override var client: IClient by column(Table.client,
            EntityTransformer(ExposedSessionRepository.Table, clientRepository))

        override var tenant: ITenant? by nullableColumn(Table.tenant,
            NullableEntityTransformer(ExposedTenantRepository.Table, tenantRepository))

        override var redirectUri: URI by column(Table.redirectUri, transformer = ExposedColumnTransformer(
            fromColumn = { URI.create(it) },
            toColumn = { it.toString() }))

        override var scope: String by column(Table.scope)

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

    object Table : UUIDTable("delegated_sessions") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expiresAt")

        val session = reference("session", ExposedSessionRepository.Table, onDelete = ReferenceOption.CASCADE)
        val client = reference("client", ExposedClientRepository.Table, onDelete = ReferenceOption.CASCADE)
        val tenant = optReference("tenant", ExposedTenantRepository.Table, onDelete = ReferenceOption.CASCADE)

        val redirectUri = varchar("redirect_uri", 2048)
        val scope = varchar("scope", 256)

        val codeHash = text("argon2_code_hash")
    }
}