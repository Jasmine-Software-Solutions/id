package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.IAttachment
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRole
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRoleAssignment
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import java.util.*

interface IRoleRepository<T : IRole> : IIdentifiedRepository<T> {
    fun entries(): List<T>
    fun findByAttachment(attachment: IAttachment, includeDescendants: Boolean = false): List<T>
}

interface IRoleAssignmentRepository<T : IRoleAssignment> : IIdentifiedRepository<T> {
    fun findByAccount(account: IAccount): List<T> = findByAccount(account.id)
    fun findByAccount(id: UUID): List<T>

    fun findByClient(client: IClient): List<T> = findByClient(client.id)
    fun findByClient(id: UUID): List<T>
}
