package app.infrastructure.repositories.organizations

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.tenants.ExposedTenantRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.organization.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.repositories.IOrganizationRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ExposedOrganizationRepository(
    val tenantRepository: ITenantRepository<ITenant>
) : ExposedIdentifiedEntityRepository<IOrganization, ExposedOrganizationRepository.Organization>(Table),
    IOrganizationRepository<IOrganization> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = Organization(row, insert, update)

    override fun transform(entity: IOrganization): Organization? = entity as? Organization

    override fun findByTenant(id: UUID): List<IOrganization> = transaction {
        Table.select { Table.tenant eq id }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    open inner class Organization(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IOrganization {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var tenant: ITenant by column(
            Table.tenant,
            EntityTransformer(ExposedTenantRepository.Table, tenantRepository)
        )

        override var name: String by column(Table.name)
    }

    object Table : UUIDTable("organizations") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val tenant = reference("tenant", ExposedTenantRepository.Table, onDelete = ReferenceOption.CASCADE)
        val name = varchar("name", 64)
    }
}
