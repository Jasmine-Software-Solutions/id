package com.jasminesoftwaresolutions.id.domain.models.tenant

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.ITenantRole

interface ITenantMembership : IIdentified, ICreated {
    var tenant: ITenant
    var account: IAccount
    var roles: Set<ITenantRole>
}
