package app.domain.services.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.ISession
import app.domain.repositories.ISessionRepository
import app.infrastructure.etc.SecureToken
import java.time.Instant
import kotlin.time.Duration

interface ISessionService<T : ISession> {
    fun create(account: IAccount, function: (T).() -> (Unit) = {}): T
}

open class StandardSessionService<T : ISession>(
    protected val repository: ISessionRepository<T>,
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