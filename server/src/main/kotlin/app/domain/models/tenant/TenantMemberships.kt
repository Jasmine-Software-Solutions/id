package app.domain.models.tenant

import app.domain.models.ICreated
import app.domain.models.IIdentified
import app.domain.models.account.IAccount

interface ITenantMembership : IIdentified, ICreated {
    var tenant: ITenant
    var account: IAccount
    var administrator: Boolean
}