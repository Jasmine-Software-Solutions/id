package com.jasminesoftwaresolutions.idinterfaces.services.auth

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.services.authorization.ITokenService
import com.jasminesoftwaresolutions.id.domain.services.authorization.Policy
import com.jasminesoftwaresolutions.id.domain.services.authorization.PolicyResult
import io.javalin.http.Context
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass

class JavalinAuthorizationService(
    val sessionRepository: ISessionRepository<IHashedSession>,
    val clientRepository: IClientRepository<out IClient>,
    val tenantRepository: ITenantRepository<out ITenant>,
    val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>,
    val tokenService: ITokenService<out IDelegatedSessionAccessToken, out IDelegatedSessionRefreshToken, out IServiceSessionAccessToken>,
    val scopeRegistry: IScopeRegistry<out IScope>,
    val scopeRepository: IScopeRepository<out IRegisteredScope>,
) : IAuthorizationService<Context, IAuthorizationContext> {
    private fun findScopeById(id: String): IScope? =
        scopeRegistry.findById(id) ?: scopeRepository.findById(id)

    inner class AccountAuthorizationContext(override val account: IAccount) : IAccountAuthorizationContext {
        override val roles: Set<IRole>
            get() = account.roles + tenantMembershipRepository.findByAccount(account).flatMap { it.roles }
    }

    inner class SessionAuthorizationContext(override val session: ISession) : ISessionAuthorizationContext {
        override val roles: Set<IRole>
            get() = account.roles + tenantMembershipRepository.findByAccount(account).flatMap { it.roles }
    }

    inner class ClientAuthorizationContext(override val client: IClient) : IClientAuthorizationContext {
        override val roles: Set<IRole>
            get() = client.roles
    }

    inner class DelegatedSessionAuthorizationContext(override val session: ISession, override val tenant: ITenant?, override val token: IDelegatedSessionAccessToken) : IDelegatedSessionAuthorizationContext {
        override val scopes: Set<IScope>
            get() = token.scopes

        override val roles: Set<IRole>
            get() {
                if (token.roles != null)
                    return token.roles!!

                val platformRoles = account.roles

                val tenantRoles = if (tenant == null) setOf()
                else tenantMembershipRepository.findByAccount(account)
                    .filter { it.tenant.id == tenant.id }
                    .flatMap { it.roles }

                return platformRoles + tenantRoles
            }
    }

    inner class ServiceSessionAuthorizationContext(override val client: IClient, override val tenant: ITenant?, override val token: IServiceSessionAccessToken) : IServiceSessionAuthorizationContext {
        override val scopes: Set<IScope>?
            get() = token.scopes

        override val roles: Set<IRole>
            get() = client.roles
    }

    override fun authenticate(request: Context): IAuthorizationContext? {
        var sessionId = request.cookie("session_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val sessionToken = request.cookie("session_token")

        val authorizationHeader = request.header("Authorization")

        val sessionPresent = sessionId != null && sessionToken != null
        val authorizationPresent = authorizationHeader != null

        if (!sessionPresent && !authorizationPresent)
            return null

        if (sessionPresent) {
            val session = sessionRepository.findById(sessionId)
                ?: return null

            if (session.verify(sessionToken) && session.expiresAt > Instant.now()) {
                sessionRepository.update(session) {
                    this.userAgent = request.userAgent()
                    this.ipAddress = request.ip()
                }

                return SessionAuthorizationContext(session)
            }

            return null
        }

        if (!authorizationPresent)
            return null

        val parts = authorizationHeader.split(" ")
        if (parts.size != 2)
            return null

        val type = parts[0]
        val token = parts[1]

        if (type == "Basic") {
            val decodedToken = String(Base64.getDecoder().decode(token))
            val parts = decodedToken.split(":", limit = 2)
            if (parts.size != 2)
                return null

            val clientId = runCatching { UUID.fromString(parts[0]) }.getOrNull()
                ?: return null

            val clientSecret = parts[1]

            val client = clientRepository.findById(clientId)
                ?: return null

            if (!client.verify(clientSecret) || client.suspended)
                return null

            return ClientAuthorizationContext(client)
        }

        if (type != "Bearer")
            return null

        val decodedToken = tokenService.decode(token)

        if (decodedToken is IServiceSessionAccessToken)
            return ServiceSessionAuthorizationContext(decodedToken.subject as IClient, decodedToken.tenant, decodedToken)

        if (decodedToken is IDelegatedSessionAccessToken)
            return DelegatedSessionAuthorizationContext(decodedToken.session, decodedToken.tenant, decodedToken)

        return null
    }
}

abstract class InterfacePolicy : Policy<IAuthorizationContext> {
    data object IsUnscopedAccountContext : InterfacePolicy() {
        override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
            if (context is IAccountAuthorizationContext && context !is IScopedAuthorizationContext)
                return PolicyResult.Pass(this, context)

            return PolicyResult.Fail()
        }
    }

    class HasPlatformPrivilege(val privilege: PlatformPrivilege) : InterfacePolicy() {
        override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
            if (context.privileges.any { it.id == privilege.id && it !is ITenantPrivilege })
                return PolicyResult.Pass(this, context)

            return PolicyResult.Fail()
        }
    }

    class HasTenantPrivilege(
        val privilege: ITenantPrivilege
    ) : InterfacePolicy() {
        companion object {
            fun inAnyLikeMembership(
                membershipRepository: ITenantMembershipRepository<out ITenantMembership>,
                privilege: UnassignedTenantPrivilege
            ): InterfacePolicy {
                return object : InterfacePolicy() {
                    override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
                        if (context !is IAccountAuthorizationContext)
                            return PolicyResult.Fail()

                        var memberships = membershipRepository.findByAccount(context.account)
                        if (context is IDelegatedSessionAuthorizationContext)
                            memberships = memberships.filter { it.tenant.id == context.tenant?.id }

                        val policy = Policy.Or<IAuthorizationContext>(memberships.map {
                            HasTenantPrivilege(TenantMembersReadPrivilege(it.tenant))
                        })

                        return policy.evaluate(context)
                    }
                }
            }
        }

        override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
            if (context.privileges.any {
                    it.id == privilege.id &&
                        it is ITenantPrivilege &&
                        it.tenant.id == privilege.tenant.id
                }) return PolicyResult.Pass(this, context)

            return PolicyResult.Fail()
        }
    }

    class HasPlatformPrivilegeOrTenantPrivilege(
        val privilege: PlatformPrivilege,
        val tenantPrivilege: ITenantPrivilege
    ) : InterfacePolicy() {
        companion object {
            fun inAnyLikeMembership(
                membershipRepository: ITenantMembershipRepository<out ITenantMembership>,
                privilege: PlatformPrivilege,
                tenantPrivilege: UnassignedTenantPrivilege
            ): InterfacePolicy {
                return object : InterfacePolicy() {
                    override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
                        if (context !is IAccountAuthorizationContext)
                            return PolicyResult.Fail()

                        var memberships = membershipRepository.findByAccount(context.account)
                        if (context is IDelegatedSessionAuthorizationContext)
                            memberships = memberships.filter { it.tenant.id == context.tenant?.id }

                        val policy = Policy.Or<IAuthorizationContext>(memberships.map {
                            HasTenantPrivilege(TenantMembersReadPrivilege(it.tenant))
                        } + HasPlatformPrivilege(privilege))

                        return policy.evaluate(context)
                    }
                }
            }
        }

        override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
            if (context.privileges.any { it.id == privilege.id && it !is ITenantPrivilege })
                return PolicyResult.Pass(this, context)

            if (context.privileges.any {
                    it.id == tenantPrivilege.id &&
                        it is ITenantPrivilege &&
                        it.tenant.id == tenantPrivilege.tenant.id
            })
                return PolicyResult.Pass(this, context)

            return PolicyResult.Fail()
        }
    }

    class HasScope(val scope: IScope) : InterfacePolicy() {
        override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
            if (context !is IScopedAuthorizationContext)
                return PolicyResult.Pass(this, context)

            if (context.scopes == null || context.scopes!!.any { it.id == scope.id })
                return PolicyResult.Pass(this, context)

            return PolicyResult.Fail()
        }
    }

    class IsSelf(val accountId: UUID?) : InterfacePolicy() {
        override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
            if (context !is IAccountAuthorizationContext || context is IScopedAuthorizationContext)
                return PolicyResult.Fail()

            if (accountId == null || context.account.id == accountId)
                return PolicyResult.Pass(this, context)

            return PolicyResult.Fail()
        }
    }

    class Is<T : IAuthorizationContext>(val type: KClass<T>) : InterfacePolicy() {
        override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
            if (type.isInstance(context))
                return PolicyResult.Pass(this, context)

            return PolicyResult.Fail()
        }
    }
}
