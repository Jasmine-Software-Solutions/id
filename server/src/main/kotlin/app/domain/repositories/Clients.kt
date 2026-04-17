package app.domain.repositories

import app.domain.models.client.IClient
import app.domain.models.client.IDelegatedSession
import app.domain.models.client.IServiceSession

interface IClientRepository<T : IClient> : IIdentifiedRepository<T>

interface IDelegatedSessionRepository<T : IDelegatedSession> : IIdentifiedRepository<T>
interface IServiceSessionRepository<T : IServiceSession> : IIdentifiedRepository<T>