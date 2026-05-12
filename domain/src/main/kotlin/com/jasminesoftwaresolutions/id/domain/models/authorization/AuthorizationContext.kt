package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.time.Instant

interface IExpiringAuthorizationContext : IAuthorizationContext {
    val expiresAt: Instant
}

interface IScopedAuthorizationContext : IAuthorizationContext {
    val scopes: Set<IScope>?
}

interface IAuthorizationContext {
    val roles: Set<IRole>

    val privileges: Set<IPrivilege>
        get() = roles.flatMap { it.privileges }.toSet()
}

interface IAccountAuthorizationContext : IAuthorizationContext {
    val account: IAccount
}

interface ISessionAuthorizationContext : IAccountAuthorizationContext, IExpiringAuthorizationContext {
    val session: ISession

    override val expiresAt: Instant
        get() = session.expiresAt

    override val account: IAccount
        get() = session.account
}

interface IClientAuthorizationContext : IAuthorizationContext {
    val client: IClient
}

interface IDelegatedSessionAuthorizationContext : ISessionAuthorizationContext, IScopedAuthorizationContext {
    val tenant: ITenant?
    val token: IDelegatedSessionAccessToken

    override val expiresAt
        get() = token.expiresAt
}

interface IServiceSessionAuthorizationContext : IClientAuthorizationContext, IScopedAuthorizationContext, IExpiringAuthorizationContext {
    val tenant: ITenant?
    val token: IServiceSessionAccessToken

    override val expiresAt: Instant
        get() = token.expiresAt
}