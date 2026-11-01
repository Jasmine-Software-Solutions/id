package com.jasminesoftwaresolutions.id.domain.services.accounts

import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedMagicLink
import com.jasminesoftwaresolutions.id.domain.models.account.IMagicLink
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IMagicLinkRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration

interface IMagicLinkService {
    fun create(account: IAccount, function: IMagicLink.() -> Unit = {}): IMagicLink

    /**
     * Records the holder's approval of [link] and verifies the account's email address.
     *
     * Approving a link that was delivered to the address proves control of that inbox, so
     * the account is marked verified. Applies to any account's approval, not just to
     * accounts created by the sign-up path.
     */
    fun approve(link: IMagicLink)
}

open class StandardMagicLinkService(
    protected val repository: IMagicLinkRepository,
    protected val accountRepository: IAccountRepository,
    protected val lifetime: Duration
) : IMagicLinkService {
    protected open fun nextDecisionToken(account: IAccount) = SecureToken()
    protected open fun nextAcceptanceToken(account: IAccount) = SecureToken()

    protected open fun finish(account: IAccount, entity: IMagicLink) = entity

    override fun create(account: IAccount, function: IMagicLink.() -> Unit): IMagicLink {
        val acceptanceToken = nextDecisionToken(account)
        val decisionToken = nextAcceptanceToken(account)

        return finish(account, repository.create {
            this.expiresAt = Instant.now().plus(lifetime.inWholeMilliseconds, ChronoUnit.MILLIS)
            this.account = account
            this.acceptanceToken = acceptanceToken
            this.decisionToken = decisionToken
            function(this)
        })
    }

    override fun approve(link: IMagicLink) {
        val persisted = link as? IHashedMagicLink
            ?: throw IllegalArgumentException("Magic link handed to approve() is not repository-backed.")

        val decidedAt = Instant.now()

        repository.update(persisted) {
            this.approved = true
            this.decidedAt = decidedAt
        }

        val account = link.account
        if (!account.emailVerified) {
            accountRepository.update(account) {
                this.emailVerified = true
            }
        }
    }
}
