package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.account.*
import java.util.*

interface IAccountRepository<T : IAccount> :
    IIdentifiedRepository<T> {
    fun findByEmail(email: String): T?
}

interface IRepositoryRelatedToAccount<T> {
    fun findByAccount(account: IAccount): T = findByAccount(account.id)
    fun findByAccount(id: UUID): T
}

interface ITOTPConfigurationRepository<T : ITOTPConfiguration> : IRepository<T>,
    IRepositoryRelatedToAccount<T> {
    override fun create(function: T.() -> Unit): T {
        throw UnsupportedOperationException()
    }

    override fun delete(entity: T) {
        throw UnsupportedOperationException()
    }
}

interface IMagicLinkRepository<T : IMagicLink> : IIdentifiedRepository<T>,
    IRepositoryRelatedToAccount<List<T>>

interface IPasswordRepository<T : IPassword> : IIdentifiedRepository<T>,
    IRepositoryRelatedToAccount<List<T>>

interface ISessionRepository<T : ISession> : IIdentifiedRepository<T>,
    IRepositoryRelatedToAccount<List<T>>