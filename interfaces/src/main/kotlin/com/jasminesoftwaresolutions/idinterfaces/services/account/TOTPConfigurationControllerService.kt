package com.jasminesoftwaresolutions.idinterfaces.services.account

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISetTOTPConfiguration
import com.jasminesoftwaresolutions.id.domain.models.account.ITOTPConfiguration
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITOTPConfigurationRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.ITOTPService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*

open class TOTPConfigurationControllerService(
    protected val totpService: ITOTPService<ISetTOTPConfiguration>,
    protected val totpConfigurationRepository: ITOTPConfigurationRepository<ITOTPConfiguration>,
    protected val accountRepository: IAccountRepository<out IAccount>,
    protected val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>
) {
    private sealed class ResolveAccountResult {
        data class Success(val account: IAccount) : ResolveAccountResult()
        object Unauthorized : ResolveAccountResult()
        object Forbidden : ResolveAccountResult()
        object NotFound : ResolveAccountResult()
    }

    open class EnableResult {
        data class Success(
            val configuration: ISetTOTPConfiguration,
            val otpAuthUri: String,
            val qrCodeUri: String
        ) : EnableResult()
        object Unauthorized : EnableResult()
        object Forbidden : EnableResult()
        object NotFound : EnableResult()
    }

    open class DisableResult {
        data class Success(val configuration: ITOTPConfiguration) : DisableResult()
        object Unauthorized : DisableResult()
        object Forbidden : DisableResult()
        object NotFound : DisableResult()
    }

    open class GetResult {
        data class Success(val configuration: ITOTPConfiguration, val qrCodeUri: String?) : GetResult()
        object Unauthorized : GetResult()
        object Forbidden : GetResult()
        object NotFound : GetResult()
    }

    open class ConfirmResult {
        data class Success(val configuration: ISetTOTPConfiguration) : ConfirmResult()
        object Unauthorized : ConfirmResult()
        object Forbidden : ConfirmResult()
        object NotFound : ConfirmResult()
        object Invalid : ConfirmResult()
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

    private fun resolveAccountForUpdate(authentication: IAuthorizationContext, accountId: UUID?): ResolveAccountResult {
        val activeAccount = accountFrom(authentication)

        if (accountId == null && activeAccount != null)
            return ResolveAccountResult.Success(activeAccount)

        if (accountId != null && activeAccount != null && accountId == activeAccount.id)
            return ResolveAccountResult.Success(activeAccount)

        if (!hasAccountsWritePrivilege(authentication))
            return if (accountId == null) ResolveAccountResult.Unauthorized else ResolveAccountResult.Forbidden

        val targetId = accountId ?: return ResolveAccountResult.Unauthorized
        val requestedAccount = accountRepository.findById(targetId)
            ?: return ResolveAccountResult.NotFound

        return ResolveAccountResult.Success(requestedAccount)
    }

    private fun qrCodeUri(otpAuthUri: String): String {
        val encoded = URLEncoder.encode(otpAuthUri, StandardCharsets.UTF_8)
        return "https://quickchart.io/chart?chs=256x256&cht=qr&chl=$encoded"
    }

    open fun enable(authentication: IAuthorizationContext, accountId: UUID? = null): EnableResult {
        val account = when (val resolved = resolveAccountForUpdate(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return EnableResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return EnableResult.Forbidden
            is ResolveAccountResult.NotFound -> return EnableResult.NotFound
        }

        val configuration = totpService.enable(account)
        val otpAuthUri = totpService.uri(configuration)

        return EnableResult.Success(
            configuration = configuration,
            otpAuthUri = otpAuthUri,
            qrCodeUri = qrCodeUri(otpAuthUri)
        )
    }

    open fun disable(authentication: IAuthorizationContext, accountId: UUID? = null): DisableResult {
        val account = when (val resolved = resolveAccountForUpdate(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return DisableResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return DisableResult.Forbidden
            is ResolveAccountResult.NotFound -> return DisableResult.NotFound
        }

        return DisableResult.Success(totpService.disable(account))
    }

    open fun get(authentication: IAuthorizationContext, accountId: UUID? = null): GetResult {
        val account = when (val resolved = resolveAccount(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return GetResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return GetResult.Forbidden
            is ResolveAccountResult.NotFound -> return GetResult.NotFound
        }

        val configuration = totpConfigurationRepository.findByAccount(account.id)
        val setConfiguration = configuration as? ISetTOTPConfiguration

        val qrCodeUri = if (setConfiguration != null && setConfiguration.enabled && setConfiguration.confirmedAt == null) {
            qrCodeUri(totpService.uri(setConfiguration))
        } else null

        return GetResult.Success(configuration, qrCodeUri)
    }

    open fun confirm(authentication: IAuthorizationContext, totp: Int, accountId: UUID? = null): ConfirmResult {
        val account = when (val resolved = resolveAccountForUpdate(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return ConfirmResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return ConfirmResult.Forbidden
            is ResolveAccountResult.NotFound -> return ConfirmResult.NotFound
        }

        val configuration = totpService.findByAccount(account.id)
            ?: return ConfirmResult.Invalid

        if (!configuration.enabled || configuration.confirmedAt != null)
            return ConfirmResult.Invalid

        if (!totpService.verify(configuration, totp).valid)
            return ConfirmResult.Invalid

        totpConfigurationRepository.update(configuration) {
            (this as ISetTOTPConfiguration).confirmedAt = Instant.now()
        }

        return ConfirmResult.Success(configuration)
    }
}
