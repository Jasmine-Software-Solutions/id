package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership

interface ITenantRepository<T : ITenant> :
    IIdentifiedRepository<T>
interface ITenantMembershipRepository<T : ITenantMembership> : IIdentifiedRepository<T>,
    IRepositoryRelatedToAccount<List<T>>