package com.jasminesoftwaresolutions.idinterfaces.services.account

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.idinterfaces.services.auth.InterfacePolicy
import java.util.*

open class PlatformRoleControllerService(
    protected val accountRepository: IAccountRepository<IAccount>,
    open val managedRoles: List<IPlatformRole> = listOf(PlatformAdministrator, PlatformMember),
) {
    private val managedRolesById = managedRoles.associateBy { it.id }

    open class AssignResult {
        data class Success(val account: IAccount, val role: IPlatformRole, val created: Boolean) : AssignResult()
        object Unauthorized : AssignResult()
        object Forbidden : AssignResult()
        object AccountNotFound : AssignResult()
        object RoleNotFound : AssignResult()
    }

    open class UnassignResult {
        data class Success(val account: IAccount, val role: IPlatformRole) : UnassignResult()
        object Unauthorized : UnassignResult()
        object Forbidden : UnassignResult()
        object AccountNotFound : UnassignResult()
        object RoleNotFound : UnassignResult()
        object NotFound : UnassignResult()
    }

    private fun resolveAccount(authentication: IAuthorizationContext, accountId: UUID?): IAccount? {
        val activeAccount = (authentication as? IAccountAuthorizationContext)
            ?.takeIf { InterfacePolicy.IsUnscopedAccountContext.passes(authentication) }
            ?.account
            ?: return null

        if (InterfacePolicy.IsSelf(accountId).passes(authentication))
            return activeAccount

        if (accountId == null)
            return null

        val targetAccount = accountRepository.findById(accountId)
            ?: return null

        return targetAccount
    }

    open fun assign(authentication: IAuthorizationContext, roleId: String, accountId: UUID? = null): AssignResult {
        val account = resolveAccount(authentication, accountId)
            ?: return AssignResult.Unauthorized

        if (!InterfacePolicy.HasPlatformPrivilege(PlatformRolesAssignPrivilege).passes(authentication))
            return AssignResult.Forbidden

        val role = managedRolesById[roleId]
            ?: return AssignResult.RoleNotFound

        if (role in account.roles)
            return AssignResult.Success(account, role, false)

        accountRepository.update(account) {
            this.roles = roles + role
        }

        return AssignResult.Success(account, role, true)
    }

    open fun unassign(authentication: IAuthorizationContext, roleId: String, accountId: UUID? = null): UnassignResult {
        val account = resolveAccount(authentication, accountId)
            ?: return UnassignResult.Unauthorized

        if (!InterfacePolicy.HasPlatformPrivilege(PlatformRolesAssignPrivilege).passes(authentication))
            return UnassignResult.Forbidden

        val role = managedRolesById[roleId]
            ?: return UnassignResult.RoleNotFound

        if (role !in account.roles)
            return UnassignResult.NotFound

        accountRepository.update(account) {
            this.roles = roles - role
        }

        return UnassignResult.Success(account, role)
    }
}
