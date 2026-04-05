package app.domain.models.client

import app.domain.models.ICreated
import app.domain.models.IExpires
import app.domain.models.IIdentified
import app.domain.models.tenant.ITenant

interface IServiceSession : IIdentified, ICreated, IExpires {
    var tenant: ITenant?
    var client: IClient

    var scope: String?

    fun isAccessToken(token: String): Boolean
}