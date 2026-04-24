package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.authorization.IRegisteredScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import java.util.*

interface IScopeRepository<T : IRegisteredScope> : IRepository<T> {
    fun entries(): Set<T>

    fun findById(id: String): T?

    fun findByClient(client: IClient): List<T> = findByClient(client.id)
    fun findByClient(id: UUID): List<T>
}
