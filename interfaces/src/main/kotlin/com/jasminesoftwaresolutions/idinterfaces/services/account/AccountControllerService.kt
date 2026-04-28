package com.jasminesoftwaresolutions.idinterfaces.services.account

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
    protected val accountRepository: IAccountRepository<IAccount>,
    protected val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>,
) {
    data class RoleDTO(
        @Transient val role: IRole,
        val id: String,
        val description: String,
        val tenantId: UUID?
    )

    class AccountDTO(
        @Transient val account: IAccount,
        val id: UUID,
        val email: String?,
        val firstName: String?,
        val lastName: String?,
        val roles: List<RoleDTO>?
    )

    open class GetAccountResult {
        data class Success(
            val account: AccountDTO
        ) : GetAccountResult()
        object Forbidden : GetAccountResult()
        object NotFound : GetAccountResult()
    }

    open class CreateAccountResult {
        data class Success(val account: AccountDTO, val created: Boolean) : CreateAccountResult()
        object Forbidden : CreateAccountResult()
    }

    open class UpdateProfileResult {
        data class Success(val account: IAccount) : UpdateProfileResult()
        data class Invalid(val message: String) : UpdateProfileResult()
        object Forbidden : UpdateProfileResult()
        object NotFound : UpdateProfileResult()
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
        val memberships = tenantMembershipRepository.findByAccount(account.id)

        return AccountDTO(
            account,
            id = account.id,
            email = if (includeEmail) account.email else null,
            firstName = if (includeProfile) account.firstName else null,
            lastName = if (includeProfile) account.lastName else null,
            roles = if (includePermissions) {
                val platformRoles = account.roles
                val tenantRoles = memberships.flatMap { it.roles }

                Streams.concat(platformRoles.stream(), tenantRoles.stream())
                    .map { RoleDTO(it, it.id, it.name, (it as? ITenantRole)?.tenant?.id) }
                    .collect(Collectors.toList())
            } else null
        )
    }

    open fun get(authentication: IAuthorizationContext, accountId: UUID?): GetAccountResult {
        if (accountId == null && authentication is IAccountAuthorizationContext)
            return GetAccountResult.Success(
                toAccountDTO(
                    account = authentication.account,
                    includeEmail = hasScope(authentication, EmailScope),
                    includeProfile = hasScope(authentication, ProfileScope),
                    includePermissions = hasScope(authentication, PermissionsScope)
                )
            )

        val account = accountId?.let(accountRepository::findById)
            ?: return GetAccountResult.NotFound

        when (authentication) {
            is ISessionAuthorizationContext -> {
                val memberships = tenantMembershipRepository.findByAccount(account)
                if (!hasPrivilege(authentication, AccountsReadPrivilege)) {
                    if (!memberships.any { hasTenantPrivilege(authentication, TenantMembersReadPrivilege(it.tenant), it.tenant) })
                        return GetAccountResult.Forbidden
                }

                return GetAccountResult.Success(
                    toAccountDTO(
                        account,
                        includeEmail = true,
                        includeProfile = true,
                        includePermissions = true
                    )
                )
            }

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

    open fun create(
        authentication: IAuthorizationContext,
        email: String,
        firstName: String,
        lastName: String
    ): CreateAccountResult {
        if (!hasPrivilege(authentication, AccountsWritePrivilege))
            return CreateAccountResult.Forbidden
        if (!hasScope(authentication, AccountsWriteScope))
            return CreateAccountResult.Forbidden

        val existingAccount = accountRepository.findByEmail(email)
        val account = existingAccount ?: accountRepository.create {
            this.email = email
            this.firstName = firstName
            this.lastName = lastName
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
            account = toAccountDTO(
                account,
                includeEmail = existingAccount == null,
                includeProfile = existingAccount == null,
                includePermissions = existingAccount == null
            ),
            created = existingAccount == null
        )
    }

    open fun updateProfile(
        authentication: IAuthorizationContext,
        accountId: UUID?,
        email: String,
        firstName: String,
        lastName: String
    ): UpdateProfileResult {
        val activeAccount = (authentication as? IAccountAuthorizationContext)
            ?.takeIf { authentication !is IScopedAuthorizationContext }
            ?.account

        val targetAccount = if (accountId == null) {
            activeAccount ?: return UpdateProfileResult.Forbidden
        } else {
            accountRepository.findById(accountId)
                ?: return UpdateProfileResult.NotFound
        }

        val isAdmin = hasPrivilege(authentication, AccountsWritePrivilege)
        val isSelf = activeAccount?.id == targetAccount.id

        if (!isSelf && !isAdmin)
            return UpdateProfileResult.Forbidden

        if (!isAdmin && email != targetAccount.email)
            return UpdateProfileResult.Forbidden

        if (isAdmin) {
            val existingByEmail = accountRepository.findByEmail(email)
            if (existingByEmail != null && existingByEmail.id != targetAccount.id)
                return UpdateProfileResult.Invalid("Email is already in use")
        }

        accountRepository.update(targetAccount) {
            if (isAdmin) {
                this.email = email
            }

            this.firstName = firstName
            this.lastName = lastName
        }

        return UpdateProfileResult.Success(targetAccount)
    }
}
