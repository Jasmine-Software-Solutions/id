package com.jasminesoftwaresolutions.idinterfaces.services.account

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.AccountsReadPrivilege
import com.jasminesoftwaresolutions.id.domain.models.authorization.IAccountAuthorizationContext
import com.jasminesoftwaresolutions.id.domain.models.authorization.IAuthorizationContext
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import com.jasminesoftwaresolutions.idinterfaces.services.auth.InterfacePolicy
import java.util.*

open class TenantMembershipControllerService<TMembership : ITenantMembership, TTenant : ITenant>(
    protected val tenantMembershipRepository: ITenantMembershipRepository<TMembership>,
    protected val tenantRepository: ITenantRepository<TTenant>,
    protected val accountRepository: IAccountRepository<out IAccount>,
) {
    private sealed class ResolveAccountResult {
        data class Success(val account: IAccount) : ResolveAccountResult()
        object Unauthorized : ResolveAccountResult()
        object Forbidden : ResolveAccountResult()
        object NotFound : ResolveAccountResult()
    }

    open class ReadResult {
        data class Success(val memberships: List<ITenantMembership>) : ReadResult()
        object Unauthorized : ReadResult()
        object Forbidden : ReadResult()
        object NotFound : ReadResult()
    }

    open class AssignResult {
        data class Success(val membership: ITenantMembership, val created: Boolean) : AssignResult()
        object Unauthorized : AssignResult()
        object Forbidden : AssignResult()
        object AccountNotFound : AssignResult()
        object TenantNotFound : AssignResult()
    }

    open class UnassignResult {
        data class Success(val membership: ITenantMembership) : UnassignResult()
        object Unauthorized : UnassignResult()
        object Forbidden : UnassignResult()
        object NotFound : UnassignResult()
    }

    private fun resolveAccount(authentication: IAuthorizationContext, accountId: UUID?): ResolveAccountResult {
        val activeAccount = (authentication as? IAccountAuthorizationContext)
            ?.takeIf { InterfacePolicy.IsUnscopedAccountContext.passes(authentication) }
            ?.account
            ?: return ResolveAccountResult.Unauthorized

        if (InterfacePolicy.IsSelf(accountId).passes(authentication))
            return ResolveAccountResult.Success(activeAccount)

        if (accountId == null)
            return ResolveAccountResult.Unauthorized

        val requestedAccount = accountRepository.findById(accountId)
            ?: return ResolveAccountResult.NotFound

        if (!InterfacePolicy.HasPlatformPrivilege(AccountsReadPrivilege).passes(authentication))
            return ResolveAccountResult.Forbidden

        return ResolveAccountResult.Success(requestedAccount)
    }

    open fun read(authentication: IAuthorizationContext, accountId: UUID? = null): ReadResult {
        val account = when (val resolved = resolveAccount(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return ReadResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return ReadResult.Forbidden
            is ResolveAccountResult.NotFound -> return ReadResult.NotFound
        }

        val memberships = tenantMembershipRepository.findByAccount(account.id)
            .sortedBy { it.tenant.name.lowercase() }

        return ReadResult.Success(memberships)
    }

    open fun assign(authentication: IAuthorizationContext, tenantId: UUID, accountId: UUID? = null): AssignResult {
        val account = when (val resolved = resolveAccount(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return AssignResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return AssignResult.Forbidden
            is ResolveAccountResult.NotFound -> return AssignResult.AccountNotFound
        }

        val tenant = tenantRepository.findById(tenantId)
            ?: return AssignResult.TenantNotFound

        val existing = tenantMembershipRepository.findByAccount(account.id)
            .firstOrNull { it.tenant.id == tenant.id }
        if (existing != null)
            return AssignResult.Success(existing, false)

        val created = tenantMembershipRepository.create {
            this.account = account
            this.tenant = tenant
        }

        return AssignResult.Success(created, true)
    }

    open fun unassign(authentication: IAuthorizationContext, tenantId: UUID, accountId: UUID? = null): UnassignResult {
        val account = when (val resolved = resolveAccount(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return UnassignResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return UnassignResult.Forbidden
            is ResolveAccountResult.NotFound -> return UnassignResult.NotFound
        }

        val membership = tenantMembershipRepository.findByAccount(account.id)
            .firstOrNull { it.tenant.id == tenantId }
            ?: return UnassignResult.NotFound

        tenantMembershipRepository.delete(membership)
        return UnassignResult.Success(membership)
    }
}
