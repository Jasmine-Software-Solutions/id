package app.infrastructure.repositories.authorization

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedAccountRepository
import app.infrastructure.repositories.tenants.ExposedTenantRepository
import app.infrastructure.util.InstantTransformer
import app.infrastructure.util.NullableEntityTransformer
import app.infrastructure.util.matchesTenant
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.jasminesoftwaresolutions.id.domain.models.authorization.IPolicy
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.repositories.IPolicyRepository
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

class ExposedPolicyRepository(
    private val tenantRepository: ITenantRepository
) : ExposedIdentifiedEntityRepository<IPolicy, ExposedPolicyRepository.PolicyEntity>(Table),
    IPolicyRepository {

    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
        = PolicyEntity(row, insert, update)

    override fun transform(entity: IPolicy): PolicyEntity? = entity as? PolicyEntity

    override fun writeDefaults(id: UUID, entity: IPolicy, it: InsertStatement<Number>) {
        super.writeDefaults(id, entity, it)
        it[Table.documentJson] = entity.document.toString()
    }

    override fun findById(id: UUID): IPolicy? = transaction {
        Table.select { Table.id eq id }
            .firstOrNull()
            ?.let { PolicyEntity(it, null, null) }
    }

    override fun entries(): List<IPolicy> = transaction {
        Table.select { org.jetbrains.exposed.sql.Op.TRUE }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { PolicyEntity(it, null, null) }
            .toList()
    }

    override fun findByTenant(tenant: ITenant?): List<IPolicy> = transaction {
        Table.select { Table.tenant.matchesTenant(tenant) }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { PolicyEntity(it, null, null) }
            .toList()
    }

    inner class PolicyEntity(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IPolicy {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var name by column(Table.name)
        override var description by nullableColumn(Table.description)

        override var tenant: ITenant? by nullableColumn(
            Table.tenant,
            NullableEntityTransformer(ExposedTenantRepository.Table, tenantRepository)
        )

        private var documentValue: JsonArray? = null

        override var document: JsonArray
            get() = documentValue ?: row?.let {
                JsonParser.parseString(it[Table.documentJson]).asJsonArray
            } ?: JsonArray()
            set(value) {
                documentValue = value
                val json = value.toString()
                row?.set(Table.documentJson, json)
                insert?.set(Table.documentJson, json)
                update?.set(Table.documentJson, json)
            }
    }

    object Table : UUIDTable("policies") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val name = varchar("name", 256)
        val description = varchar("description", 1024).nullable()
        val documentJson = text("document_json")

        val tenant = optReference("tenant_id", ExposedTenantRepository.Table)
    }
}
