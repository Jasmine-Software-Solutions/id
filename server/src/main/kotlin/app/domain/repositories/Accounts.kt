package app.domain.repositories

import app.domain.models.account.*
import java.util.*

interface IAccountRepository : IIdentifiedRepository<IAccount> {
    fun findByEmail(email: String): IAccount?
}

interface IRepositoryRelatedToAccount<T> {
    fun findByAccount(account: IAccount): T = findByAccount(account.id)
    fun findByAccount(id: UUID): T
}

interface ITOTPConfigurationRepository : IRepository<ITOTPConfiguration>, IRepositoryRelatedToAccount<ITOTPConfiguration> {
    override fun create(function: ITOTPConfiguration.() -> Unit): ITOTPConfiguration {
        throw UnsupportedOperationException()
    }

    override fun delete(entity: ITOTPConfiguration) {
        throw UnsupportedOperationException()
    }
}

interface IMagicLinkRepository : IIdentifiedRepository<IMagicLink>, IRepositoryRelatedToAccount<List<IMagicLink>>

interface IPasswordRepository : IIdentifiedRepository<IPassword>, IRepositoryRelatedToAccount<List<IPassword>>

interface ISessionRepository : IIdentifiedRepository<ISession>, IRepositoryRelatedToAccount<List<ISession>>