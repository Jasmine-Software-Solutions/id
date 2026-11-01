package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.authorization.IRegisteredScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import java.util.*

interface IScopeRepository : IRepository<IRegisteredScope> {
    fun entries(): Set<IRegisteredScope>
    fun findById(id: String): IRegisteredScope?
    fun findByClient(client: IClient): List<IRegisteredScope> = findByClient(client.id)
    fun findByClient(id: UUID): List<IRegisteredScope>
}
