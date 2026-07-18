package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.tenant.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITeam
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import java.util.*

interface ITenantRepository<T : ITenant> :
    IIdentifiedRepository<T>
interface ITenantMembershipRepository<T : ITenantMembership> : IIdentifiedRepository<T>,
    IRepositoryRelatedToAccount<List<T>> {
    fun findByTenant(tenant: ITenant, includeDescendants: Boolean = false): List<T> =
        findByTenant(tenant.id, includeDescendants)
    fun findByTenant(id: UUID, includeDescendants: Boolean = false): List<T>
    fun findByOrganization(organization: IOrganization, includeDescendants: Boolean = false): List<T> =
        findByOrganization(organization.id, includeDescendants)
    fun findByOrganization(id: UUID, includeDescendants: Boolean = false): List<T>
    fun findByTeam(team: ITeam): List<T> = findByTeam(team.id)
    fun findByTeam(id: UUID): List<T>
}

interface IOrganizationRepository<T : IOrganization> : IIdentifiedRepository<T> {
    fun findByTenant(tenant: ITenant): List<T> = findByTenant(tenant.id)
    fun findByTenant(id: UUID): List<T>
}


interface ITeamRepository<T : ITeam> : IIdentifiedRepository<T> {
    fun findByOrganization(organization: IOrganization): List<T> = findByOrganization(organization.id)
    fun findByOrganization(id: UUID): List<T>
}