package app.domain.models.account

import app.domain.models.ICreated
import app.domain.models.IExpires
import app.domain.models.IIdentified

interface ISession : IIdentified, ICreated, IExpires {
    var userAgent: String
    var ipAddress: String

    var account: IAccount

    fun verify(token: String): Boolean
}

interface IHashedSession : ISession {
    var token: String
}