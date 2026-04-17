package app.infrastructure.repositories.clients

import app.domain.models.account.ISession
import app.domain.models.client.IClient
import app.domain.models.client.IDelegatedSession
import app.domain.models.client.IHashedUnnegotiatedDelegatedSession
import app.domain.models.tenant.ITenant
import app.domain.repositories.IClientRepository
import app.domain.repositories.IDelegatedSessionRepository
import app.domain.repositories.ISessionRepository
import app.domain.repositories.ITenantRepository
import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedSessionRepository
import app.infrastructure.repositories.tenants.ExposedTenantRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.ExposedColumnTransformer
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableEntityTransformer
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.net.URI
import java.time.Instant

class ExposedDelegatedSessionRepository(
    val sessionRepository: ISessionRepository<ISession>,
    val clientRepository: IClientRepository<IClient>,
    val tenantRepository: ITenantRepository<ITenant>
) : ExposedIdentifiedEntityRepository<IDelegatedSession, ExposedDelegatedSessionRepository.DelegatedSession>(Table, DelegatedSession::class),
    IDelegatedSessionRepository<IDelegatedSession> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = DelegatedSession(row, insert, update)

    open inner class DelegatedSession(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IDelegatedSession {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var refreshedAt: Instant by column(Table.refreshedAt, InstantTransformer)
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

        override var state: String? by nullableColumn(Table.state)
        override var scope: String? by nullableColumn(Table.scope)

        override fun isAccessToken(token: String): Boolean {
            TODO("Not yet implemented")
        }

        override fun isRefreshToken(token: String): Boolean {
            TODO("Not yet implemented")
        }

        override fun isValidAt(instant: Instant): Boolean {
            return instant < expiresAt && instant > createdAt
        }
    }

    open inner class UnnegotiatedDelegatedSession(
        row: ResultRow?,
        insert: InsertStatement<Number>?,
        update: UpdateStatement?
    ) : DelegatedSession(row, insert, update), IHashedUnnegotiatedDelegatedSession {
        override var authorizationCode: String by requiredColumn(Table.authorizationCode)

        override fun isAuthorizationCode(code: String): Boolean {
            TODO("Not yet implemented")
        }
    }

    object Table : UUIDTable("delegated_sessions") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val refreshedAt = long("refreshed_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expiresAt")

        val session = reference("session", ExposedSessionRepository.Table, onDelete = ReferenceOption.CASCADE)
        val client = reference("client", ExposedClientRepository.Table, onDelete = ReferenceOption.CASCADE)
        val tenant = optReference("tenant", ExposedTenantRepository.Table, onDelete = ReferenceOption.CASCADE)

        val redirectUri = varchar("redirect_uri", 2048)
        val state = varchar("state", 256).nullable()
        val scope = varchar("scope", 256).nullable()

        val authorizationCode = varchar("authorization_code", 64).nullable()
    }
}