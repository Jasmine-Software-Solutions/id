package app.infrastructure.repositories.clients

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.tenants.ExposedTenantRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableEntityTransformer
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IServiceSession
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IServiceSessionRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

class ExposedServiceSessionRepository(
    val tenantRepository: ITenantRepository<ITenant>,
    val clientRepository: IClientRepository<IClient>
) : ExposedIdentifiedEntityRepository<IServiceSession, ExposedServiceSessionRepository.ServiceSession>(Table, ServiceSession::class),
    IServiceSessionRepository<IServiceSession> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = ServiceSession(row, insert, update)

    open inner class ServiceSession(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IServiceSession {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var expiresAt: Instant by column(Table.expiresAt, InstantTransformer)

        override var tenant: ITenant? by nullableColumn(Table.tenant,
            NullableEntityTransformer(ExposedTenantRepository.Table, tenantRepository))

        override var client: IClient by column(Table.client,
            EntityTransformer(ExposedClientRepository.Table, clientRepository))

        override var scope: String? by nullableColumn(Table.scope)

        override fun isAccessToken(token: String): Boolean {
            TODO("Not yet implemented")
        }
    }

    object Table : UUIDTable("service_sessions") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val expiresAt = long("expiresAt")

        val tenant = optReference("tenant_id", ExposedTenantRepository.Table, onDelete = ReferenceOption.CASCADE)
        val client = reference("client_id", ExposedClientRepository.Table, onDelete = ReferenceOption.CASCADE)

        val scope = varchar("scope", 256).nullable()
    }
}