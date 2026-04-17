package app.domain.repositories

import app.domain.models.authentication.IAuthenticationFlow

interface IAuthenticationFlowRepository<T : IAuthenticationFlow> : IIdentifiedRepository<T>