package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.*
import com.jasminesoftwaresolutions.id.domain.models.tenant.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITeam
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.time.Instant
import java.util.*

fun IRole.isSameAssignmentAs(other: IRole): Boolean =
    id == other.id && attachment.isSameAssignmentTargetAs(other.attachment)

fun IAttachment.isSameAssignmentTargetAs(other: IAttachment): Boolean =
    when {
        this is ITenantAttachment && other is ITenantAttachment -> tenant.id == other.tenant.id
        this is IOrganizationAttachment && other is IOrganizationAttachment -> organization.id == other.organization.id
        this is ITeamAttachment && other is ITeamAttachment -> team.id == other.team.id
        else -> type == other.type
    }

interface IRole: IIdentified, ICreated {
    var name: String
    var attachment: IAttachment
}

interface IPlatformRole : IRole {
    override var attachment: IAttachment
        get() = PlatformAttachment
        set(value) {
            require(value.type == PlatformAttachment.type)
        }
}

interface ITenantRole : IRole {
    override var attachment: IAttachment
        get() = TenantAttachment(tenant)
        set(value) {
            tenant = (value as ITenantAttachment).tenant
        }

    var tenant: ITenant
}

interface IOrganizationRole : IRole {
    override var attachment: IAttachment
        get() = OrganizationAttachment(organization)
        set(value) {
            organization = (value as IOrganizationAttachment).organization
        }

    var organization: IOrganization
}

interface ITeamRole : IRole {
    override var attachment: IAttachment
        get() = TeamAttachment(team)
        set(value) {
            team = (value as ITeamAttachment).team
        }

    var team: ITeam
}

open class PlatformRole(
    override var id: UUID,
    override var name: String,
    override val createdAt: Instant
) : IPlatformRole

open class TenantRole(
    override var tenant: ITenant,
    override var id: UUID,
    override var name: String,
    override val createdAt: Instant
) : ITenantRole

open class OrganizationRole(
    override var organization: IOrganization,
    override var id: UUID,
    override var name: String,
    override val createdAt: Instant
) : IOrganizationRole

open class TeamRole(
    override var team: ITeam,
    override var id: UUID,
    override var name: String,
    override val createdAt: Instant
) : ITeamRole
