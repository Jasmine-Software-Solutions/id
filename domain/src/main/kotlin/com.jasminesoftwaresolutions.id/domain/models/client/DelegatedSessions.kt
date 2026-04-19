package com.jasminesoftwaresolutions.id.domain.models.client

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.net.URI
import java.time.Instant

interface IDelegatedSession : IIdentified,
    ICreated, IExpires {
    var refreshedAt: Instant

    var session: ISession
    var client: IClient

    var tenant: ITenant?
    var redirectUri: URI
    var state: String?

    var scope: String?

    fun isAccessToken(token: String): Boolean
    fun isRefreshToken(token: String): Boolean

    fun isValidAt(instant: Instant): Boolean
}

interface IUnnegotiatedDelegatedSession : IDelegatedSession,
    ICreated, IExpires {
    fun isAuthorizationCode(code: String): Boolean
}

interface IHashedUnnegotiatedDelegatedSession :
    IUnnegotiatedDelegatedSession {
    var authorizationCode: String
}