package app.domain.models.oauth2

import app.domain.models.ICreated
import app.domain.models.IExpires
import app.domain.models.IIdentified
import app.domain.models.client.IClient
import app.domain.models.tenant.ITenant

interface IServiceAccessToken : IIdentified, ICreated, IExpires {
    var client: IClient
    var tenant: ITenant

    fun verify(token: String): Boolean
}

interface IHashedServiceAccessToken : IServiceAccessToken {
    var token: String
}