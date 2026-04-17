package app.domain.services.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IMagicLink
import app.domain.repositories.IMagicLinkRepository
import app.infrastructure.etc.SecureToken
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration

interface IMagicLinkService<T : IMagicLink> {
    fun create(account: IAccount, function: (T).() -> Unit = {}): T
}

open class StandardMagicLinkService<T : IMagicLink>(
    protected val repository: IMagicLinkRepository<T>,
    protected val lifetime: Duration
) : IMagicLinkService<T> {
    protected open fun nextDecisionToken(account: IAccount) = SecureToken()
    protected open fun nextAcceptanceToken(account: IAccount) = SecureToken()

    protected open fun T.create(account: IAccount, function: (T).() -> Unit) {
        function(this)
    }

    protected open fun finish(account: IAccount, entity: T) = entity

    override fun create(account: IAccount, function: (T).() -> Unit): T {
        val acceptanceToken = nextDecisionToken(account)
        val decisionToken = nextAcceptanceToken(account)

        return finish(account, repository.create {
            this.expiresAt = Instant.now().plus(lifetime.inWholeMilliseconds, ChronoUnit.MILLIS)

            this.acceptanceToken = acceptanceToken
            this.decisionToken = decisionToken
            create(account, function)
        })
    }
}