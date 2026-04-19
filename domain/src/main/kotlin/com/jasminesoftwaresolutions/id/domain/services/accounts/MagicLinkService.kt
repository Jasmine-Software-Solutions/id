package com.jasminesoftwaresolutions.id.domain.services.accounts

import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IMagicLink
import com.jasminesoftwaresolutions.id.domain.repositories.IMagicLinkRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration

interface IMagicLinkService<T : IMagicLink> {
    fun create(account: IAccount, function: (T).() -> Unit = {}): T
}

open class StandardMagicLinkService<T : IMagicLink>(
    protected val repository: IMagicLinkRepository<out T>,
    protected val lifetime: Duration
) : IMagicLinkService<T> {
    protected open fun nextDecisionToken(account: IAccount) =
        SecureToken()
    protected open fun nextAcceptanceToken(account: IAccount) =
        SecureToken()

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