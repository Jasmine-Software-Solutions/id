package com.jasminesoftwaresolutions.id.domain.services.accounts

import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IEmailChange
import com.jasminesoftwaresolutions.id.domain.repositories.IEmailChangeRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration

interface IEmailChangeService {
    /**
     * Create a new email change request for the given [account] with the proposed [newEmail].
     * Returns the created [IEmailChange] entity with a verification token.
     */
    fun create(account: IAccount, newEmail: String, function: IEmailChange.() -> Unit = {}): IEmailChange
}

open class StandardEmailChangeService(
    protected val repository: IEmailChangeRepository,
    protected val lifetime: Duration
) : IEmailChangeService {
    protected open fun nextVerificationToken(account: IAccount) = SecureToken()

    protected open fun finish(account: IAccount, entity: IEmailChange) = entity

    override fun create(account: IAccount, newEmail: String, function: IEmailChange.() -> Unit): IEmailChange {
        val token = nextVerificationToken(account)

        return finish(account, repository.create {
            this.expiresAt = Instant.now().plus(lifetime.inWholeMilliseconds, ChronoUnit.MILLIS)
            this.account = account
            this.newEmail = newEmail
            this.verificationToken = token
            this.confirmed = false
            function(this)
        })
    }
}
