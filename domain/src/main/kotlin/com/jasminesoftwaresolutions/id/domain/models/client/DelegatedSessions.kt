package com.jasminesoftwaresolutions.id.domain.models.client

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.net.URI

interface IDelegatedSession : IIdentified, ICreated, IExpires {
    var session: ISession
    var client: IClient
    var tenant: ITenant?

    var redirectUri: URI
    var scope: String

    var code: String

    fun verify(code: String): Boolean
}

interface IHashedDelegatedSession : IDelegatedSession