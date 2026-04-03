package app.domain.models.oauth2

import app.domain.models.ICreated
import app.domain.models.IExpires
import app.domain.models.IIdentified
import app.domain.models.account.ISession
import app.domain.models.client.IClient
import app.domain.models.tenant.ITenant
import java.net.URI
import java.time.Instant

interface IDelegatedAccessToken : IIdentified, ICreated {
    var refreshedAt: Instant

    var tenant: ITenant
    var session: ISession
    var client: IClient

    var scope: String
    var redirectUri: URI

    fun verifyAccessToken(token: String): Boolean
    fun verifyRefreshToken(token: String): Boolean
}

interface IUnnegotiatedDelegatedAccessToken : IDelegatedAccessToken, ICreated, IExpires {
    fun verifyAuthorizationCode(code: String): Boolean
}

interface IHashedUnnegotiatedDelegatedAccessToken : IUnnegotiatedDelegatedAccessToken {
    var authorizationCode: String
}