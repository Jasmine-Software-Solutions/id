package app.infrastructure.repositories.tenants

import app.domain.models.tenant.ITenant
import app.domain.repositories.ITenantRepository
import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.util.InstantTransformer
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

class ExposedTenantRepository
    : ExposedIdentifiedEntityRepository<ITenant, ExposedTenantRepository.Tenant>(Table, Tenant::class),
    ITenantRepository<ITenant> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = Tenant(row, insert, update)

    open inner class Tenant(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), ITenant {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)
        override var name: String by column(Table.name)
        override var suspended: Boolean by column(Table.suspended)
    }

    object Table : UUIDTable("tenants") {
        val createdAt = long("created_at")
        val name = varchar("name", 64)

        val suspended = bool("suspended").default(false)
    }
}