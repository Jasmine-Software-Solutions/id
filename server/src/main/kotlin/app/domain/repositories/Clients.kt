package app.domain.repositories

import app.domain.models.client.IClient
import app.domain.models.client.IDelegatedSession
import app.domain.models.client.IServiceSession

interface IClientRepository : IIdentifiedRepository<IClient>

interface IDelegatedSessionRepository : IIdentifiedRepository<IDelegatedSession>
interface IServiceSessionRepository : IIdentifiedRepository<IServiceSession>