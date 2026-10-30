package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.client.IServiceSession

interface IClientRepository : IIdentifiedRepository<IClient> {
    fun entries(): List<IClient>
    fun findByFqdn(fqdn: String): IClient?
}

interface IDelegatedSessionRepository : IIdentifiedRepository<IDelegatedSession>

interface IServiceSessionRepository : IIdentifiedRepository<IServiceSession>
