package app.infrastructure.repositories.authorization

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedAccountRepository
import app.infrastructure.repositories.tenants.ExposedTenantRepository
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableEntityTransformer
import app.infrastructure.util.matchesTenant
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRole
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.repositories.IRoleRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ExposedRoleRepository(
    private val tenantRepository: ITenantRepository
) : ExposedIdentifiedEntityRepository<IRole, ExposedRoleRepository.Role>(Table), IRoleRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = Role(row, insert, update)

    override fun transform(entity: IRole): Role? = entity as? Role

    override fun findById(id: UUID): IRole? = transaction {
        Table.select { Table.id eq id }
            .firstOrNull()
            ?.let { Role(it, null, null) }
    }

    override fun entries(): List<IRole> = transaction {
        Table.select { org.jetbrains.exposed.sql.Op.TRUE }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { Role(it, null, null) }
            .toList()
    }

    override fun findByTenant(tenant: ITenant?): List<IRole> = transaction {
        Table.select { Table.tenant.matchesTenant(tenant) }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { Role(it, null, null) }
            .toList()
    }

    override fun findByName(name: String): List<IRole> = transaction {
        Table.select { Table.name eq name }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { Role(it, null, null) }
            .toList()
    }

    inner class Role(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IRole {
        override val createdAt: Instant by column(ExposedAccountRepository.Table.createdAt, InstantTransformer)

        override var name by column(Table.name)
        override var description by nullableColumn(Table.description)

        override var tenant: ITenant? by nullableColumn(
            Table.tenant,
            NullableEntityTransformer(ExposedTenantRepository.Table, tenantRepository)
        )
    }

    object Table : UUIDTable("roles") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val name = varchar("name", 128)
        val description = varchar("description", 1024).nullable()

        val tenant = optReference("tenant_id", ExposedTenantRepository.Table)
    }
}
