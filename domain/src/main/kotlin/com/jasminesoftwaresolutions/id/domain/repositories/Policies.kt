package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.IPolicy
import com.jasminesoftwaresolutions.id.domain.models.authorization.IPolicyAssignment
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRole
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.util.*

interface IPolicyRepository : IIdentifiedRepository<IPolicy> {
    fun entries(): List<IPolicy>

    /** Policies scoped to [tenant], or platform-scoped policies when [tenant] is `null`. */
    fun findByTenant(tenant: ITenant?): List<IPolicy>
}

interface IPolicyAssignmentRepository : IIdentifiedRepository<IPolicyAssignment> {
    fun findByAccount(account: IAccount): List<IPolicyAssignment> = findByAccount(account.id)
    fun findByAccount(id: UUID): List<IPolicyAssignment>
    fun findByClient(client: IClient): List<IPolicyAssignment> = findByClient(client.id)
    fun findByClient(id: UUID): List<IPolicyAssignment>
    fun findByRole(role: IRole): List<IPolicyAssignment> = findByRole(role.id)
    fun findByRole(id: UUID): List<IPolicyAssignment>
    fun findByPolicy(policy: IPolicy): List<IPolicyAssignment> = findByPolicy(policy.id)
    fun findByPolicy(id: UUID): List<IPolicyAssignment>
}
