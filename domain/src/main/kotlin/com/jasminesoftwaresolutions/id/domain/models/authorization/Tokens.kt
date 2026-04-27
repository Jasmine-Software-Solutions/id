package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.time.Instant

interface IToken : IIdentified {
    val token: String

    val expiresAt: Instant
    val issuedAt: Instant
    val notBefore: Instant

    val subject: IIdentified
    val audience: IClient

    val tenant: ITenant?
}

interface IDelegatedSessionAccessToken : IToken {
    val delegatedSession: IDelegatedSession
    val session: ISession

    val scopes: Set<IScope>
    val roles: Set<IRole>?
}

interface IDelegatedSessionRefreshToken : IToken {
    val delegatedSession: IDelegatedSession
    val session: ISession
}

interface IServiceSessionAccessToken : IToken {
    val scopes: Set<IScope>?
    val roles: Set<IRole>?
}