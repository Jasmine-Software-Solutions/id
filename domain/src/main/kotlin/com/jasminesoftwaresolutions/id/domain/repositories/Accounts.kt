package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.account.*
import java.util.*

interface IAccountRepository : IIdentifiedRepository<IAccount> {
    fun findByEmail(email: String): IAccount?
    fun entries(): List<IAccount>
}

interface IRepositoryRelatedToAccount<T> {
    fun findByAccount(account: IAccount): List<T> = findByAccount(account.id)
    fun findByAccount(id: UUID): List<T>
}

interface ITOTPConfigurationRepository : IRepository<ITOTPConfiguration> {
    fun findByAccount(account: IAccount): ITOTPConfiguration = findByAccount(account.id)
    fun findByAccount(id: UUID): ITOTPConfiguration

    override fun create(function: ITOTPConfiguration.() -> Unit): ITOTPConfiguration {
        throw UnsupportedOperationException()
    }

    override fun delete(entity: ITOTPConfiguration) {
        throw UnsupportedOperationException()
    }
}

interface IMagicLinkRepository : IIdentifiedRepository<IHashedMagicLink>, IRepositoryRelatedToAccount<IHashedMagicLink>

interface IPasswordRepository : IIdentifiedRepository<IHashedPassword>, IRepositoryRelatedToAccount<IHashedPassword>

interface ISessionRepository : IIdentifiedRepository<IHashedSession>, IRepositoryRelatedToAccount<IHashedSession>

interface IEmailChangeRepository : IIdentifiedRepository<IHashedEmailChange>, IRepositoryRelatedToAccount<IHashedEmailChange>
