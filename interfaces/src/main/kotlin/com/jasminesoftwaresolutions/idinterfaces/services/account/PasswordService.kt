package com.jasminesoftwaresolutions.idinterfaces.services.account

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedPassword
import com.jasminesoftwaresolutions.id.domain.models.account.IPassword
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IPasswordRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import java.util.*

open class PasswordService<T : IHashedPassword>(
    protected val passwordRepository: IPasswordRepository<T>,
    protected val accountRepository: IAccountRepository<out IAccount>,
    protected val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>,
) {
    private sealed class ResolveAccountResult {
        data class Success(val account: IAccount) : ResolveAccountResult()
        object Unauthorized : ResolveAccountResult()
        object Forbidden : ResolveAccountResult()
        object NotFound : ResolveAccountResult()
    }

    open class UpdateResult {
        data class Success(val password: IPassword) : UpdateResult()
        object Unauthorized : UpdateResult()
        object Forbidden : UpdateResult()
        object NotFound : UpdateResult()
    }

    open class LastResult {
        data class Success(val password: IPassword?) : LastResult()
        object Unauthorized : LastResult()
        object Forbidden : LastResult()
        object NotFound : LastResult()
    }

    private fun accountFrom(authentication: IAuthorizationContext): IAccount? =
        (authentication as? IAccountAuthorizationContext)
            ?.takeIf { authentication !is IScopedAuthorizationContext }
            ?.account

    private fun hasAccountsWritePrivilege(authentication: IAuthorizationContext): Boolean =
        authentication.privileges.any { it.id == AccountsWritePrivilege.id }

    private fun canReadAccount(authentication: IAuthorizationContext, account: IAccount): Boolean {
        if (authentication.privileges.any { it.id == AccountsReadPrivilege.id })
            return true
        if (authentication.privileges.any { it.id == MembersReadPrivilege.id && it !is ITenantPrivilege })
            return true

        val readableTenantIds = authentication.privileges
            .filterIsInstance<ITenantPrivilege>()
            .filter { it.id == MembersReadPrivilege.id }
            .map { it.tenant.id }
            .toSet()
        if (readableTenantIds.isEmpty())
            return false

        return tenantMembershipRepository.findByAccount(account.id)
            .any { it.tenant.id in readableTenantIds }
    }

    private fun resolveAccount(authentication: IAuthorizationContext, accountId: UUID?): ResolveAccountResult {
        val activeAccount = accountFrom(authentication)
            ?: return ResolveAccountResult.Unauthorized

        if (accountId == null || accountId == activeAccount.id)
            return ResolveAccountResult.Success(activeAccount)

        val requestedAccount = accountRepository.findById(accountId)
            ?: return ResolveAccountResult.NotFound

        if (!canReadAccount(authentication, requestedAccount))
            return ResolveAccountResult.Forbidden

        return ResolveAccountResult.Success(requestedAccount)
    }

    open fun update(authentication: IAuthorizationContext, password: String, accountId: UUID? = null): UpdateResult {
        val activeAccount = accountFrom(authentication)
        val account = when {
            accountId == null && activeAccount != null -> activeAccount
            accountId != null && activeAccount != null && accountId == activeAccount.id -> activeAccount
            hasAccountsWritePrivilege(authentication) -> {
                val targetId = accountId ?: return UpdateResult.Unauthorized
                accountRepository.findById(targetId) ?: return UpdateResult.NotFound
            }
            accountId != null -> return UpdateResult.Forbidden
            else -> return UpdateResult.Unauthorized
        }

        val created = passwordRepository.create {
            this.account = account
            this.password = password
        }

        return UpdateResult.Success(created)
    }

    open fun last(authentication: IAuthorizationContext, accountId: UUID? = null): LastResult {
        val account = when (val resolved = resolveAccount(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return LastResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return LastResult.Forbidden
            is ResolveAccountResult.NotFound -> return LastResult.NotFound
        }

        val active = passwordRepository.findByAccount(account.id)
            .maxByOrNull { it.createdAt }

        return LastResult.Success(active)
    }
}
