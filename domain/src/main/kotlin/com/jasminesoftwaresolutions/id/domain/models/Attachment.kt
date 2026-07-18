package com.jasminesoftwaresolutions.id.domain.models

import com.jasminesoftwaresolutions.id.domain.models.tenant.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITeam
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant

enum class AttachmentType {
    GLOBAL,
    TENANT,
    ORGANIZATION,
    TEAM
}

interface IAttachment {
    val type: AttachmentType
    val tenant: ITenant?
        get() = null
    val organization: IOrganization?
        get() = null
    val team: ITeam?
        get() = null
}

interface IPlatformAttachment : IAttachment {
    override val type: AttachmentType
        get() = AttachmentType.GLOBAL
}

object PlatformAttachment : IPlatformAttachment

interface ITenantAttachment : IAttachment {
    override val type: AttachmentType
        get() = AttachmentType.TENANT

    override val tenant: ITenant
}

data class TenantAttachment(override val tenant: ITenant) : ITenantAttachment

interface IOrganizationAttachment : IAttachment {
    override val type: AttachmentType
        get() = AttachmentType.ORGANIZATION

    override val tenant: ITenant
        get() = organization.tenant

    override val organization: IOrganization
}

data class OrganizationAttachment(override val organization: IOrganization) : IOrganizationAttachment

interface ITeamAttachment : IAttachment {
    override val type: AttachmentType
        get() = AttachmentType.TEAM

    override val tenant: ITenant
        get() = team.organization.tenant

    override val organization: IOrganization
        get() = team.organization

    override val team: ITeam
}

data class TeamAttachment(override val team: ITeam) : ITeamAttachment
