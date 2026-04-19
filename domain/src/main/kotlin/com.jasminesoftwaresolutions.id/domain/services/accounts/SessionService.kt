package com.jasminesoftwaresolutions.id.domain.services.accounts

import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.repositories.ISessionRepository
import java.time.Instant
import kotlin.time.Duration

interface ISessionService<T : ISession> {
    fun create(account: IAccount, function: (T).() -> (Unit) = {}): T
}

open class StandardSessionService<T : ISession>(
    protected val repository: ISessionRepository<out T>,
    protected val lifetime: Duration
) : ISessionService<T> {
    protected open fun nextToken(account: IAccount) = SecureToken()

    protected open fun T.create(account: IAccount, function: (T).() -> (Unit) = {}) {
        function(this)
    }

    protected open fun finish(account: IAccount, entity: T) = entity

    override fun create(account: IAccount, function: (T).() -> Unit): T {
        val token = nextToken(account)
        return finish(account, repository.create {
            this.expiresAt = Instant.now().plusMillis(lifetime.inWholeMilliseconds)
            this.account = account

            this.token = token
            create(account, function)
        })
    }
}