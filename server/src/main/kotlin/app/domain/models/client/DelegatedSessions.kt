package app.domain.models.client

import app.domain.models.ICreated
import app.domain.models.IExpires
import app.domain.models.IIdentified
import app.domain.models.account.ISession
import app.domain.models.tenant.ITenant
import java.net.URI
import java.time.Instant

interface IDelegatedSession : IIdentified, ICreated, IExpires {
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

interface IUnnegotiatedDelegatedSession : IDelegatedSession, ICreated, IExpires {
    fun isAuthorizationCode(code: String): Boolean
}

interface IHashedUnnegotiatedDelegatedSession : IUnnegotiatedDelegatedSession {
    var authorizationCode: String
}