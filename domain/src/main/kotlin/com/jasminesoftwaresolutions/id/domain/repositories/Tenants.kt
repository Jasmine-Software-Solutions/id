package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import java.util.*

interface ITenantRepository : IIdentifiedRepository<ITenant> {
    fun entries(): List<ITenant>
}

interface ITenantMembershipRepository : IIdentifiedRepository<ITenantMembership>, IRepositoryRelatedToAccount<ITenantMembership> {
    fun findByTenant(tenant: ITenant): List<ITenantMembership> = findByTenant(tenant.id)
    fun findByTenant(id: UUID): List<ITenantMembership>
}
