package com.jasminesoftwaresolutions.id.domain.models.tenant

import com.jasminesoftwaresolutions.id.domain.models.IAttachment
import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount

interface ITenantMembership : IIdentified, ICreated {
    var attachment: IAttachment
    var account: IAccount

    val tenant: ITenant?
        get() = attachment.tenant

    val organization: IOrganization?
        get() = attachment.organization

    val team: ITeam?
        get() = attachment.team
}
