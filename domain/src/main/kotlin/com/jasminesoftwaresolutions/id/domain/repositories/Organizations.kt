package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.organization.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.util.*

interface IOrganizationRepository<T : IOrganization> : IIdentifiedRepository<T> {
    fun findByTenant(tenant: ITenant): List<T> = findByTenant(tenant.id)
    fun findByTenant(id: UUID): List<T>
}
