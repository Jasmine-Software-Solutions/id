package app.infrastructure.repositories.authorization

import app.infrastructure.entities.ExposedIdentifiedEntity
import app.infrastructure.repositories.ExposedIdentifiedEntityRepository
import app.infrastructure.repositories.accounts.ExposedAccountRepository
import app.infrastructure.repositories.tenants.ExposedOrganizationRepository
import app.infrastructure.repositories.tenants.ExposedTeamRepository
import app.infrastructure.util.ExposedAttachmentColumns
import app.infrastructure.util.InstantTransformer
import com.jasminesoftwaresolutions.id.domain.models.AttachmentType
import com.jasminesoftwaresolutions.id.domain.models.IAttachment
import com.jasminesoftwaresolutions.id.domain.models.PlatformAttachment
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRole
import com.jasminesoftwaresolutions.id.domain.models.tenant.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITeam
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.repositories.IOrganizationRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IRoleRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITeamRepository
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
    private val tenantRepository: ITenantRepository<ITenant>,
    private val organizationRepository: IOrganizationRepository<IOrganization>,
    private val teamRepository: ITeamRepository<ITeam>
) : ExposedIdentifiedEntityRepository<IRole, ExposedRoleRepository.Role>(Table), IRoleRepository<IRole> {
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

    override fun findByAttachment(attachment: IAttachment, includeDescendants: Boolean): List<IRole> = transaction {
        Table.select { Table.attachment.matchesAttachment(attachment, includeDescendants) }
            .orderBy(Table.createdAt, SortOrder.ASC)
            .map { Role(it, null, null) }
            .toList()
    }

    inner class Role(
        row: ResultRow? = null,
        insert: InsertStatement<Number>? = null,
        update: UpdateStatement? = null
    ) : ExposedIdentifiedEntity(Table, row, insert, update), IRole {
        private var attachmentValue: IAttachment? = null

        override val createdAt: Instant by column(ExposedAccountRepository.Table.createdAt, InstantTransformer)

        override var name by column(Table.name)

        override var attachment: IAttachment
            get() = attachmentValue ?: row?.let {
                Table.attachment.read(it, tenantRepository, organizationRepository, teamRepository)
            } ?: PlatformAttachment
            set(value) {
                attachmentValue = value
                Table.attachment.write(row, insert, update, value)
            }
    }

    object Table : UUIDTable("roles") {
        val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

        val name = varchar("name", 128)

        val attachment = ExposedAttachmentColumns(
            type = varchar("attachment_type", 32).default(AttachmentType.GLOBAL.name),
            tenant = optReference("tenant_id", app.infrastructure.repositories.tenants.ExposedTenantRepository.Table),
            organization = optReference("organization_id", ExposedOrganizationRepository.Table),
            team = optReference("team_id", ExposedTeamRepository.Table)
        )
    }
}
