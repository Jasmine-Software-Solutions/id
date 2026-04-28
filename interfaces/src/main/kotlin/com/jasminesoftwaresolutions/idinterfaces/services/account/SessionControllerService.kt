package com.jasminesoftwaresolutions.idinterfaces.services.account

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ISessionRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import java.time.Instant
import java.util.*

open class SessionControllerService<T : ISession>(
    protected val sessionRepository: ISessionRepository<T>,
    protected val accountRepository: IAccountRepository<out IAccount>,
    protected val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>,
) {
    private sealed class ResolveAccountResult {
        data class Success(val account: IAccount) : ResolveAccountResult()
        object Unauthorized : ResolveAccountResult()
        object Forbidden : ResolveAccountResult()
        object NotFound : ResolveAccountResult()
    }

    open class GetSessionsResult {
        data class Success(val sessions: List<ISession>) : GetSessionsResult()
        object Unauthorized : GetSessionsResult()
        object Forbidden : GetSessionsResult()
        object NotFound : GetSessionsResult()
    }

    open class RevokeSessionResult {
        data class Success(val session: ISession) : RevokeSessionResult()
        object Unauthorized : RevokeSessionResult()
        object Forbidden : RevokeSessionResult()
        object NotFound : RevokeSessionResult()
    }

    open class RevokeAllSessionsResult {
        data class Success(val sessions: List<ISession>) : RevokeAllSessionsResult()
        object Unauthorized : RevokeAllSessionsResult()
        object Forbidden : RevokeAllSessionsResult()
        object NotFound : RevokeAllSessionsResult()
    }

    private fun accountFrom(authentication: IAuthorizationContext) =
        (authentication as? IAccountAuthorizationContext)
            ?.takeIf { authentication !is IScopedAuthorizationContext }
            ?.account

    private fun hasAccountsWritePrivilege(authentication: IAuthorizationContext): Boolean =
        authentication.privileges.any { it.id == AccountsWritePrivilege.id }

    private fun hasMembersWritePrivilege(authentication: IAuthorizationContext): Boolean =
        authentication.privileges.any { it.id == MembersWritePrivilege.id && it !is ITenantPrivilege }

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

    private fun canRevokeWithTenantMembersWritePrivilege(authentication: IAuthorizationContext, account: IAccount): Boolean {
        val writableTenantIds = authentication.privileges
            .filterIsInstance<ITenantPrivilege>()
            .filter { it.id == MembersWritePrivilege.id }
            .map { it.tenant.id }
            .toSet()
        if (writableTenantIds.isEmpty())
            return false

        return tenantMembershipRepository.findByAccount(account.id)
            .any { it.tenant.id in writableTenantIds }
    }

    private fun resolveAccountForRevoke(authentication: IAuthorizationContext, accountId: UUID?): ResolveAccountResult {
        val activeAccount = accountFrom(authentication)

        if (accountId == null && activeAccount != null)
            return ResolveAccountResult.Success(activeAccount)

        if (accountId != null && activeAccount != null && accountId == activeAccount.id)
            return ResolveAccountResult.Success(activeAccount)

        val targetId = accountId ?: return ResolveAccountResult.Unauthorized
        val requestedAccount = accountRepository.findById(targetId)
            ?: return ResolveAccountResult.NotFound

        if (hasAccountsWritePrivilege(authentication))
            return ResolveAccountResult.Success(requestedAccount)

        if (hasMembersWritePrivilege(authentication))
            return ResolveAccountResult.Success(requestedAccount)

        if (canRevokeWithTenantMembersWritePrivilege(authentication, requestedAccount))
            return ResolveAccountResult.Success(requestedAccount)

        return ResolveAccountResult.Forbidden
    }

    open fun getAll(authentication: IAuthorizationContext, accountId: UUID? = null): GetSessionsResult {
        val account = when (val resolved = resolveAccount(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return GetSessionsResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return GetSessionsResult.Forbidden
            is ResolveAccountResult.NotFound -> return GetSessionsResult.NotFound
        }

        val sessions = sessionRepository.findByAccount(account.id)
            .sortedByDescending { it.createdAt }
            .filter { it.expiresAt > Instant.now() }

        return GetSessionsResult.Success(sessions)
    }

    open fun revoke(authentication: IAuthorizationContext, sessionId: UUID, accountId: UUID? = null): RevokeSessionResult {
        val account = when (val resolved = resolveAccountForRevoke(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return RevokeSessionResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return RevokeSessionResult.Forbidden
            is ResolveAccountResult.NotFound -> return RevokeSessionResult.NotFound
        }

        val session = sessionRepository.findById(sessionId)
            ?: return RevokeSessionResult.NotFound

        if (session.account.id != account.id)
            return RevokeSessionResult.NotFound

        sessionRepository.update(session) {
            this.expiresAt = Instant.now()
        }

        return RevokeSessionResult.Success(session)
    }

    open fun revokeAll(authentication: IAuthorizationContext, accountId: UUID? = null): RevokeAllSessionsResult {
        val account = when (val resolved = resolveAccountForRevoke(authentication, accountId)) {
            is ResolveAccountResult.Success -> resolved.account
            is ResolveAccountResult.Unauthorized -> return RevokeAllSessionsResult.Unauthorized
            is ResolveAccountResult.Forbidden -> return RevokeAllSessionsResult.Forbidden
            is ResolveAccountResult.NotFound -> return RevokeAllSessionsResult.NotFound
        }

        val sessions = sessionRepository.findByAccount(account.id)
        for (session in sessions)
            sessionRepository.update(session) {
                this.expiresAt = Instant.now()
            }

        return RevokeAllSessionsResult.Success(sessions)
    }
}
