package com.jasminesoftwaresolutions.id.domain.services.accounts

import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.repositories.ISessionRepository
import java.time.Instant
import kotlin.time.Duration

interface ISessionService {
    fun create(account: IAccount, function: ISession.() -> Unit = {}): ISession
}

open class StandardSessionService(
    protected val repository: ISessionRepository,
    protected val lifetime: Duration
) : ISessionService {
    protected open fun nextToken(account: IAccount) = SecureToken()

    protected open fun finish(account: IAccount, entity: ISession) = entity

    override fun create(account: IAccount, function: ISession.() -> Unit): ISession {
        val token = nextToken(account)
        return finish(account, repository.create {
            this.expiresAt = Instant.now().plusMillis(lifetime.inWholeMilliseconds)
            this.account = account
            this.token = token
            function(this)
        })
    }
}
