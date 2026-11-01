package com.jasminesoftwaresolutions.id.domain.models.client

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import java.net.URI
import java.time.Instant

interface IDelegatedSession : IIdentified, ICreated, IExpires {
    var refreshedAt: Instant?

    var session: IHashedSession
    var client: IClient

    var redirectUri: URI
    var scopes: Set<IScope>
    var nonce: String?

    val code: IHashedDelegatedSessionCode
}

interface IDelegatedSessionCode : ICreated, IExpires {
    var code: String

    fun verify(code: String): Boolean
}

interface IHashedDelegatedSessionCode : IDelegatedSessionCode