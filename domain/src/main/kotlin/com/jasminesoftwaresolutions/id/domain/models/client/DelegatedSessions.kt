package com.jasminesoftwaresolutions.id.domain.models.client

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import java.net.URI
import java.time.Instant

interface IDelegatedSession : IIdentified, ICreated, IExpires {
    var refreshedAt: Instant?

    var session: ISession
    var client: IClient
    var tenant: ITenant?

    var redirectUri: URI
    var scopes: Set<IScope>

    val code: IDelegatedSessionCode
}

interface IDelegatedSessionCode : ICreated, IExpires {
    var code: String

    fun verify(code: String): Boolean
}

interface IHashedDelegatedSessionCode : IDelegatedSessionCode