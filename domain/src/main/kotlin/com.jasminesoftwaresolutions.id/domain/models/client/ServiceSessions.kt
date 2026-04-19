package com.jasminesoftwaresolutions.id.domain.models.client

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant

interface IServiceSession : IIdentified,
    ICreated, IExpires {
    var tenant: ITenant?
    var client: IClient

    var scope: String?

    fun isAccessToken(token: String): Boolean
}