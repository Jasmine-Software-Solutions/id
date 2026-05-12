package com.jasminesoftwaresolutions.idinterfaces.services.account

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.AccountsReadPrivilege
import com.jasminesoftwaresolutions.id.domain.models.authorization.AccountsWritePrivilege
import com.jasminesoftwaresolutions.id.domain.models.authorization.IAccountAuthorizationContext
import com.jasminesoftwaresolutions.id.domain.models.authorization.IAuthorizationContext
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ISessionRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.idinterfaces.services.auth.InterfacePolicy
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

    private fun resolveAccount(authentication: IAuthorizationContext, accountId: UUID?): ResolveAccountResult {
        val activeAccount = (authentication as? IAccountAuthorizationContext)?.account
            ?.takeIf { InterfacePolicy.IsUnscopedAccountContext.passes(authentication) }
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

    private fun resolveAccountForRevoke(authentication: IAuthorizationContext, accountId: UUID?): ResolveAccountResult {
        val activeAccount = (authentication as? IAccountAuthorizationContext)?.account
            ?.takeIf { InterfacePolicy.IsUnscopedAccountContext.passes(authentication) }
            ?: return ResolveAccountResult.Unauthorized

        if (InterfacePolicy.IsSelf(accountId).passes(authentication))
            return ResolveAccountResult.Success(activeAccount)

        if (accountId == null)
            return ResolveAccountResult.Unauthorized

        val requestedAccount = accountRepository.findById(accountId)
            ?: return ResolveAccountResult.NotFound

        if (!InterfacePolicy.HasPlatformPrivilege(AccountsReadPrivilege).passes(authentication))
            return ResolveAccountResult.Forbidden

        if (!InterfacePolicy.HasPlatformPrivilege(AccountsWritePrivilege).passes(authentication))
            return ResolveAccountResult.Forbidden

        return ResolveAccountResult.Success(requestedAccount)
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
