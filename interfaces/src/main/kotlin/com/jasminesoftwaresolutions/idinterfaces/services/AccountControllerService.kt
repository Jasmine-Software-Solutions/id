package com.jasminesoftwaresolutions.idinterfaces.services

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import org.jetbrains.kotlin.com.google.common.collect.Streams
import java.util.*
import java.util.stream.Collectors

open class AccountControllerService(
    protected val accountRepository: IAccountRepository<out IAccount>,
    protected val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>,
) {
    data class RoleDTO(
        val id: String,
        val description: String,
        val tenantId: UUID?
    )

    data class AccountDTO(
        val id: UUID,
        val email: String?,
        val firstName: String?,
        val lastName: String?,
        val roles: List<RoleDTO>?
    )

    data class CreateAccountRequest(
        val email: String,
        val firstName: String,
        val lastName: String
    )

    open class GetAccountResult {
        data class Success(val account: AccountDTO) : GetAccountResult()
        object Forbidden : GetAccountResult()
        object NotFound : GetAccountResult()
    }

    open class CreateAccountResult {
        data class Success(val account: AccountDTO, val created: Boolean) : CreateAccountResult()
        object Forbidden : CreateAccountResult()
    }

    private fun hasScope(authentication: IAuthorizationContext, scope: IScope): Boolean {
        if (authentication !is IScopedAuthorizationContext)
            return true

        val scopes = authentication.scopes
            ?: return true

        return scopes.any { it.id == scope.id }
    }

    private fun hasPrivilege(authentication: IAuthorizationContext, privilege: IPrivilege): Boolean =
        authentication.privileges.any { it.id == privilege.id }

    private fun hasTenantPrivilege(authentication: IAuthorizationContext, privilege: IPrivilege, tenant: ITenant): Boolean =
        authentication.privileges.any {
            it.id == privilege.id &&
                it is ITenantPrivilege &&
                it.tenant.id == tenant.id
        }

    private fun isMemberOfTenant(account: IAccount, tenant: ITenant): Boolean =
        tenantMembershipRepository.findByAccount(account.id).any { it.tenant.id == tenant.id }

    private fun toAccountDTO(
        account: IAccount,
        includeEmail: Boolean = true,
        includeProfile: Boolean = true,
        includePermissions: Boolean = true
    ): AccountDTO {
        val memberships = if (includePermissions) tenantMembershipRepository.findByAccount(account.id) else emptyList()

        return AccountDTO(
            id = account.id,
            email = if (includeEmail) account.email else null,
            firstName = if (includeProfile) account.firstName else null,
            lastName = if (includeProfile) account.lastName else null,
            roles = if (includePermissions) {
                val platformRoles = account.roles
                val tenantRoles = memberships.flatMap { it.roles }

                Streams.concat(platformRoles.stream(), tenantRoles.stream())
                    .map { RoleDTO(it.id, it.description, (it as? ITenantRole)?.tenant?.id) }
                    .collect(Collectors.toList())
            } else null
        )
    }

    open fun getMe(authentication: IAuthorizationContext): GetAccountResult {
        if (authentication !is IAccountAuthorizationContext)
            return GetAccountResult.Forbidden

        val limitedByScope = authentication is IScopedAuthorizationContext && authentication.scopes != null

        val includeEmail = !limitedByScope || hasScope(authentication, EmailScope)
        val includeProfile = !limitedByScope || hasScope(authentication, ProfileScope)
        val includePermissions = !limitedByScope || hasScope(authentication, PermissionsScope)

        return GetAccountResult.Success(
            toAccountDTO(
                account = authentication.account,
                includeEmail = includeEmail,
                includeProfile = includeProfile,
                includePermissions = includePermissions
            )
        )
    }

    open fun getById(authentication: IAuthorizationContext, accountId: UUID): GetAccountResult {
        val account = accountRepository.findById(accountId)
            ?: return GetAccountResult.NotFound

        when (authentication) {
            is IDelegatedSessionAuthorizationContext -> {
                val canReadAnyAccount = hasPrivilege(authentication, AccountsReadPrivilege) &&
                    hasScope(authentication, AccountsReadScope)

                if (!canReadAnyAccount) {
                    val tenant = authentication.tenant
                        ?: return GetAccountResult.Forbidden

                    if (!hasTenantPrivilege(authentication, TenantMembersReadPrivilege(tenant), tenant))
                        return GetAccountResult.Forbidden
                    if (!hasScope(authentication, TenantMembersScope))
                        return GetAccountResult.Forbidden
                    if (!isMemberOfTenant(account, tenant))
                        return GetAccountResult.NotFound
                }

                return GetAccountResult.Success(toAccountDTO(account))
            }

            is IServiceSessionAuthorizationContext -> {
                if (!hasPrivilege(authentication, AccountsReadPrivilege))
                    return GetAccountResult.Forbidden
                if (!hasScope(authentication, AccountsReadScope))
                    return GetAccountResult.Forbidden

                val tenant = authentication.token.tenant
                if (tenant != null && !isMemberOfTenant(account, tenant))
                    return GetAccountResult.NotFound

                return GetAccountResult.Success(toAccountDTO(account))
            }

            else -> return GetAccountResult.Forbidden
        }
    }

    open fun create(authentication: IAuthorizationContext, request: CreateAccountRequest): CreateAccountResult {
        if (!hasPrivilege(authentication, AccountsWritePrivilege))
            return CreateAccountResult.Forbidden
        if (!hasScope(authentication, AccountsWriteScope))
            return CreateAccountResult.Forbidden

        val existingAccount = accountRepository.findByEmail(request.email)
        val account = existingAccount ?: accountRepository.create {
            this.email = request.email
            this.firstName = request.firstName
            this.lastName = request.lastName
        }

        val tenant = when (authentication) {
            is IDelegatedSessionAuthorizationContext -> authentication.tenant
            is IServiceSessionAuthorizationContext -> authentication.token.tenant
            else -> null
        }

        if (tenant != null) {
            if (!hasTenantPrivilege(authentication, TenantMembersWritePrivilege(tenant), tenant))
                return CreateAccountResult.Forbidden
            if (!hasScope(authentication, TenantMembersWriteScope))
                return CreateAccountResult.Forbidden

            if (!isMemberOfTenant(account, tenant)) {
                tenantMembershipRepository.create {
                    this.tenant = tenant
                    this.account = account
                }
            }
        }

        return CreateAccountResult.Success(
            account = toAccountDTO(account),
            created = existingAccount == null
        )
    }
}
