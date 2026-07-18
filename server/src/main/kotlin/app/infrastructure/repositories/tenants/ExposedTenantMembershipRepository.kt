package app.infrastructure.repositories.tenants

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedAccountRepository
import app.infrastructure.util.EntityTransformer
import app.infrastructure.util.ExposedAttachmentColumns
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.IAttachment
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.tenant.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITeam
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.*
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
    val tenantRepository: ITenantRepository<ITenant>,
    val organizationRepository: IOrganizationRepository<IOrganization>,
    val teamRepository: ITeamRepository<ITeam>,
    val accountRepository: IAccountRepository<IAccount>
) : ExposedIdentifiedEntityRepository<ITenantMembership, ExposedTenantMembershipRepository.TenantMembership>(Table),
    ITenantMembershipRepository<ITenantMembership> {
    override fun read(row: ResultRow?, insert: InsertStatement<Number>?, update: UpdateStatement?)
            = TenantMembership(row, insert, update)

    override fun transform(entity: ITenantMembership): TenantMembership? = entity as? TenantMembership

    override fun findByAccount(id: UUID): List<ITenantMembership> = transaction {
        val memberships = Table.select { Table.account eq id }
            .orderBy(Table.createdAt, org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()

        return@transaction memberships
    }

    override fun findByTenant(id: UUID, includeDescendants: Boolean): List<ITenantMembership> = transaction {
        Table.select { Table.attachment.matchesTenant(id, includeDescendants) }
            .orderBy(Table.createdAt, org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    override fun findByOrganization(id: UUID, includeDescendants: Boolean): List<ITenantMembership> = transaction {
        Table.select { Table.attachment.matchesOrganization(id, includeDescendants) }
            .orderBy(Table.createdAt, org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    override fun findByTeam(id: UUID): List<ITenantMembership> = transaction {
        Table.select { Table.attachment.matchesTeam(id) }
            .orderBy(Table.createdAt, org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { read(it, null, null) }
            .toList()
    }

    open inner class TenantMembership(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), ITenantMembership {
        private var attachmentValue: IAttachment? = null

        override val createdAt: Instant by column(Table.createdAt, InstantTransformer)

        override var attachment: IAttachment
            get() = attachmentValue ?: readAttachment()
            set(value) {
                attachmentValue = value
                Table.attachment.write(row, insert, update, value, allowGlobal = false)
            }

        override var account: IAccount by column(Table.account,
            EntityTransformer(ExposedAccountRepository.Table, accountRepository))

        private fun readAttachment(): IAttachment {
            val row = row ?: throw UnsupportedOperationException("Membership attachment is not readable until it is persisted")

            return Table.attachment.read(row, tenantRepository, organizationRepository, teamRepository)
        }
    }

    object Table : UUIDTable("tenant_memberships") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val attachment = ExposedAttachmentColumns(
            type = varchar("attachment_type", 32),
            tenant = optReference("tenant", ExposedTenantRepository.Table, onDelete = ReferenceOption.CASCADE),
            organization = optReference("organization", ExposedOrganizationRepository.Table, onDelete = ReferenceOption.CASCADE),
            team = optReference("team", ExposedTeamRepository.Table, onDelete = ReferenceOption.CASCADE)
        )
        val account = reference("account", ExposedAccountRepository.Table.id)

        init {
            index(false, account)
        }
    }
}
