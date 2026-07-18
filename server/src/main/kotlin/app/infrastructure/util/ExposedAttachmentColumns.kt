package app.infrastructure.util

import app.infrastructure.repositories.tenants.ExposedOrganizationRepository
import app.infrastructure.repositories.tenants.ExposedTeamRepository
import app.infrastructure.repositories.tenants.ExposedTenantRepository
import com.jasminesoftwaresolutions.id.domain.models.*
import com.jasminesoftwaresolutions.id.domain.models.tenant.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITeam
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.repositories.IOrganizationRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITeamRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.util.*

class ExposedAttachmentColumns(
    val type: Column<String>,
    val tenant: Column<EntityID<UUID>?>,
    val organization: Column<EntityID<UUID>?>,
    val team: Column<EntityID<UUID>?>
) {
    fun read(
        row: ResultRow,
        tenantRepository: ITenantRepository<ITenant>,
        organizationRepository: IOrganizationRepository<IOrganization>,
        teamRepository: ITeamRepository<ITeam>
    ): IAttachment = when (AttachmentType.valueOf(row[type])) {
        AttachmentType.GLOBAL -> PlatformAttachment
        AttachmentType.TENANT -> TenantAttachment(tenantRepository.findById(row[tenant]!!.value)!!)
        AttachmentType.ORGANIZATION -> OrganizationAttachment(organizationRepository.findById(row[organization]!!.value)!!)
        AttachmentType.TEAM -> TeamAttachment(teamRepository.findById(row[team]!!.value)!!)
    }

    fun write(
        row: ResultRow?,
        insert: InsertStatement<Number>?,
        update: UpdateStatement?,
        attachment: IAttachment,
        allowGlobal: Boolean = true
    ) {
        if (!allowGlobal && attachment.type == AttachmentType.GLOBAL)
            throw IllegalStateException("Global attachment is not allowed here")

        set(row, insert, update, type, attachment.type.name)
        set(row, insert, update, tenant, attachment.tenant?.id?.let { EntityID(it, ExposedTenantRepository.Table) })
        set(row, insert, update, organization, attachment.organization?.id?.let { EntityID(it, ExposedOrganizationRepository.Table) })
        set(row, insert, update, team, attachment.team?.id?.let { EntityID(it, ExposedTeamRepository.Table) })
    }

    fun matchesTenant(id: UUID, includeDescendants: Boolean): Op<Boolean> {
        val tenantId = EntityID(id, ExposedTenantRepository.Table)
        return if (includeDescendants)
            tenant eq tenantId
        else
            (type eq AttachmentType.TENANT.name) and (tenant eq tenantId)
    }

    fun matchesOrganization(id: UUID, includeDescendants: Boolean): Op<Boolean> {
        val organizationId = EntityID(id, ExposedOrganizationRepository.Table)
        return if (includeDescendants)
            organization eq organizationId
        else
            (type eq AttachmentType.ORGANIZATION.name) and (organization eq organizationId)
    }

    fun matchesTeam(id: UUID): Op<Boolean> =
        (type eq AttachmentType.TEAM.name) and (team eq EntityID(id, ExposedTeamRepository.Table))

    fun matchesAttachment(attachment: IAttachment, includeDescendants: Boolean = false): Op<Boolean> = when (attachment) {
        is ITenantAttachment -> matchesTenant(attachment.tenant.id, includeDescendants)
        is IOrganizationAttachment -> matchesOrganization(attachment.organization.id, includeDescendants)
        is ITeamAttachment -> matchesTeam(attachment.team.id)
        else -> type eq AttachmentType.GLOBAL.name
    }

    private fun <T> set(
        row: ResultRow?,
        insert: InsertStatement<Number>?,
        update: UpdateStatement?,
        column: Column<T>,
        value: T
    ) {
        row?.set(column, value)
        insert?.set(column, value)
        update?.set(column, value)
    }
}
