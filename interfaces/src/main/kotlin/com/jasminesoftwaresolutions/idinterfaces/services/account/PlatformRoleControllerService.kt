package com.jasminesoftwaresolutions.idinterfaces.services.account

import com.jasminesoftwaresolutions.id.domain.models.AttachmentType
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IRoleAssignmentRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IRoleRepository
import com.jasminesoftwaresolutions.idinterfaces.services.auth.InterfacePolicy
import java.util.*

open class PlatformRoleControllerService(
    protected val accountRepository: IAccountRepository<IAccount>,
    protected val assignmentRepository: IRoleAssignmentRepository<out IRoleAssignment>,
    protected val roleRepository: IRoleRepository<out IRole>,
) {
    open val managedRoles: List<IRole>
        get() = roleRepository.entries()
            .filter { it.attachment.type == AttachmentType.GLOBAL }

    open class AssignResult {
        data class Success(val account: IAccount, val role: IRole, val created: Boolean) : AssignResult()
        object Unauthorized : AssignResult()
        object Forbidden : AssignResult()
        object AccountNotFound : AssignResult()
        object RoleNotFound : AssignResult()
    }

    open class UnassignResult {
        data class Success(val account: IAccount, val role: IRole) : UnassignResult()
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

        val role = roleRepository.findById(roleId)
            ?.takeIf { it.attachment.type == AttachmentType.GLOBAL }
            ?: return AssignResult.RoleNotFound

        val existing = assignmentRepository.findByAccount(account)
            .firstOrNull { it.role.isSameAssignmentAs(role) }

        if (existing != null)
            return AssignResult.Success(account, role, false)

        @Suppress("UNCHECKED_CAST")
        (assignmentRepository as IRoleAssignmentRepository<IRoleAssignment>).create {
            this.account = account
            this.client = null
            this.role = role
        }

        return AssignResult.Success(account, role, true)
    }

    open fun unassign(authentication: IAuthorizationContext, roleId: String, accountId: UUID? = null): UnassignResult {
        val account = resolveAccount(authentication, accountId)
            ?: return UnassignResult.Unauthorized

        if (!InterfacePolicy.HasPlatformPrivilege(PlatformRolesAssignPrivilege).passes(authentication))
            return UnassignResult.Forbidden

        val role = roleRepository.findById(roleId)
            ?.takeIf { it.attachment.type == AttachmentType.GLOBAL }
            ?: return UnassignResult.RoleNotFound

        val assignment = assignmentRepository.findByAccount(account)
            .firstOrNull { it.role.isSameAssignmentAs(role) }
            ?: return UnassignResult.NotFound

        @Suppress("UNCHECKED_CAST")
        (assignmentRepository as IRoleAssignmentRepository<IRoleAssignment>).delete(assignment)

        return UnassignResult.Success(account, role)
    }
}
