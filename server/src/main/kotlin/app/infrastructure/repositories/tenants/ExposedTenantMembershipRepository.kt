package app.infrastructure.repositories.tenants

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedAccountRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ExposedTenantMembershipRepository(
    val tenantRepository: ITenantRepository,
    val accountRepository: IAccountRepository
) : ExposedIdentifiedEntityRepository<ITenantMembership, ExposedTenantMembershipRepository.TenantMembership>(Table),
    ITenantMembershipRepository {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = TenantMembership(row, insert, update)

    override fun transform(entity: ITenantMembership): TenantMembership? = entity as? TenantMembership

    override fun findByAccount(id: UUID): List<ITenantMembership> = transaction {
        Table.select { Table.account eq id }
            .orderBy(Table.createdAt, org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    override fun findByTenant(id: UUID): List<ITenantMembership> = transaction {
        Table.select { Table.tenant eq id }
            .orderBy(Table.createdAt, org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    open inner class TenantMembership(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), ITenantMembership {
        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var tenant: ITenant by column(Table.tenant,
            EntityTransformer(ExposedTenantRepository.Table, tenantRepository))

        override var account: IAccount by column(Table.account,
            EntityTransformer(ExposedAccountRepository.Table, accountRepository))
    }

    object Table : UUIDTable("tenant_memberships") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
        val tenant = reference("tenant", ExposedTenantRepository.Table, onDelete = ReferenceOption.CASCADE)
        val account = reference("account", ExposedAccountRepository.Table.id)

        init {
            index(false, account)
            index(false, tenant)
        }
    }
}
