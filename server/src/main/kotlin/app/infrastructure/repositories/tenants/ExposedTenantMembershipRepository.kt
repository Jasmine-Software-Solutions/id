package app.infrastructure.repositories.tenants

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedAccountRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.ExposedColumnTransformer
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.ITenantRole
import com.jasminesoftwaresolutions.id.domain.models.authorization.TenantAdministrator
import com.jasminesoftwaresolutions.id.domain.models.authorization.TenantMember
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class ExposedTenantMembershipRepository(
    val tenantRepository: ITenantRepository<ITenant>,
    val accountRepository: IAccountRepository<IAccount>
) : ExposedIdentifiedEntityRepository<ITenantMembership, ExposedTenantMembershipRepository.TenantMembership>(Table, TenantMembership::class),
    ITenantMembershipRepository<ITenantMembership> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = TenantMembership(row, insert, update)

    override fun findByAccount(id: UUID): List<ITenantMembership> = transaction {
        val memberships = Table.select { Table.account eq id }
            .orderBy(Table.createdAt, org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()

        return@transaction memberships
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

        override val roles: Set<ITenantRole> by column(
            Table.roles, ExposedColumnTransformer(
            fromColumn = { it.split(",").mapNotNull {
                if (it == TenantAdministrator.id)
                    TenantAdministrator(this.tenant)
                else if (it == TenantMember.id)
                    TenantMember(this.tenant)
                else null
            }.toSet() },
            toColumn = { it.map { it.id }.joinToString(",") },
        ))
    }

    object Table : UUIDTable("tenant_memberships") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val tenant = reference("tenant", ExposedTenantRepository.Table.id)
        val account = reference("account", ExposedAccountRepository.Table.id)

        val roles = text("roles").clientDefault { TenantMember.id }

        init {
            uniqueIndex(tenant, account)
        }
    }
}