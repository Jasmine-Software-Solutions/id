package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.client.IServiceSession

interface IClientRepository<T : IClient> :
    IIdentifiedRepository<T>

interface IDelegatedSessionRepository<T : IDelegatedSession> :
    IIdentifiedRepository<T>
interface IServiceSessionRepository<T : IServiceSession> :
    IIdentifiedRepository<T>