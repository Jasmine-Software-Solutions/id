package com.jasminesoftwaresolutions.idinterfaces.services.account

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.services.authorization.Policy
import com.jasminesoftwaresolutions.id.domain.services.authorization.PolicyResult
import com.jasminesoftwaresolutions.idinterfaces.services.auth.InterfacePolicy
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
                    includeEmail = InterfacePolicy.HasScope(EmailScope).evaluate(authentication) is PolicyResult.Pass,
                    includeProfile = InterfacePolicy.HasScope(ProfileScope).evaluate(authentication) is PolicyResult.Pass,
                    includePermissions = InterfacePolicy.HasScope(PermissionsScope).evaluate(authentication) is PolicyResult.Pass
                )
            )

        val account = accountId?.let(accountRepository::findById)
            ?: return GetAccountResult.NotFound

        when (authentication) {
            is ISessionAuthorizationContext -> {
                val policy = InterfacePolicy.HasPlatformPrivilegeOrTenantPrivilege.inAnyLikeMembership(
                    tenantMembershipRepository,
                    AccountsReadPrivilege,
                    TenantMembersReadPrivilege
                )

                if (!policy.passes(authentication))
                    return GetAccountResult.Forbidden

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
                val policy = Policy.Or<IAuthorizationContext>(listOf(
                    Policy.And<IAuthorizationContext>(listOf(
                        // Can read any account
                        InterfacePolicy.HasPlatformPrivilege(AccountsReadPrivilege),
                        InterfacePolicy.HasScope(AccountsReadScope)
                    )),
                    Policy.And<IAuthorizationContext>(listOf(
                        InterfacePolicy.HasScope(TenantMembersScope),
                        InterfacePolicy.HasTenantPrivilege.inAnyLikeMembership(
                            tenantMembershipRepository,
                            TenantMembersReadPrivilege,
                        )
                    ))
                ))

                if (!policy.passes(authentication))
                    return GetAccountResult.Forbidden

                return GetAccountResult.Success(toAccountDTO(account))
            }

            is IServiceSessionAuthorizationContext -> {
                val policy = Policy.And<IAuthorizationContext>(listOf(
                    InterfacePolicy.HasScope(AccountsReadScope),
                    InterfacePolicy.HasPlatformPrivilege(AccountsReadPrivilege)
                ))

                if (!policy.passes(authentication))
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
        val policy = Policy.And<IAuthorizationContext>(listOf(
            InterfacePolicy.HasScope(AccountsWriteScope),
            InterfacePolicy.HasPlatformPrivilege(AccountsWritePrivilege)
        ))

        if (!policy.passes(authentication))
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
            val policy = Policy.And<IAuthorizationContext>(listOf(
                InterfacePolicy.HasScope(TenantMembersWriteScope),
                InterfacePolicy.HasTenantPrivilege(TenantMembersWritePrivilege(tenant))
            ))

            if (!policy.passes(authentication))
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
            ?.takeIf { InterfacePolicy.IsUnscopedAccountContext.passes(authentication) }
            ?.account

        val targetAccount = if (accountId == null) {
            activeAccount ?: return UpdateProfileResult.Forbidden
        } else {
            accountRepository.findById(accountId)
                ?: return UpdateProfileResult.NotFound
        }

        val isAdmin = InterfacePolicy.HasPlatformPrivilege(AccountsWritePrivilege).passes(authentication)
        val isSelf = InterfacePolicy.IsSelf(targetAccount.id).passes(authentication)

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
