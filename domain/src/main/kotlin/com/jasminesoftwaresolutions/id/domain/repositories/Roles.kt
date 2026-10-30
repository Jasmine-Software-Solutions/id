package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRole
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRoleAssignment
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.util.*

interface IRoleRepository : IIdentifiedRepository<IRole> {
    fun entries(): List<IRole>

    /** Roles scoped to [tenant], or platform-scoped roles when [tenant] is `null`. */
    fun findByTenant(tenant: ITenant?): List<IRole>
    fun findByName(name: String): List<IRole>
}

interface IRoleAssignmentRepository : IIdentifiedRepository<IRoleAssignment> {
    fun findByAccount(account: IAccount): List<IRoleAssignment> = findByAccount(account.id)
    fun findByAccount(id: UUID): List<IRoleAssignment>
    fun findByClient(client: IClient): List<IRoleAssignment> = findByClient(client.id)
    fun findByClient(id: UUID): List<IRoleAssignment>
    fun findByRole(role: IRole): List<IRoleAssignment> = findByRole(role.id)
    fun findByRole(id: UUID): List<IRoleAssignment>
}
