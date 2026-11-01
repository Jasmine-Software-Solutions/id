package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import java.time.Instant

interface IToken : IIdentified {
    val token: String

    val expiresAt: Instant
    val issuedAt: Instant
    val notBefore: Instant

    val subject: IIdentified
    val audience: List<String>

    val scopes: Set<String>
}

interface IAccessToken : IToken {
    val delegatedSession: IDelegatedSession
    val session: ISession
}

interface IRefreshToken : IToken {
    val delegatedSession: IDelegatedSession
    val session: ISession
}

interface IServiceToken : IToken

interface IIDToken : IIdentified {
    val token: String

    val issuedAt: Instant
    val expiresAt: Instant

    val subject: String
    val audience: String
    val issuer: String

    val nonce: String?
    val claims: Map<String, Any?>
}