package com.jasminesoftwaresolutions.idinterfaces.services

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.accounts.IJWT
import com.jasminesoftwaresolutions.id.domain.services.accounts.IJWTService
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.services.authorization.Policy
import com.jasminesoftwaresolutions.id.domain.services.authorization.PolicyResult
import io.javalin.http.Context
import java.time.Instant
import java.util.*

class JavalinAuthorizationService(
    val sessionRepository: ISessionRepository<out ISession>,
    val clientRepository: IClientRepository<out IClient>,
    val tenantRepository: ITenantRepository<out ITenant>,
    val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>,
    val jwtService: IJWTService,
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

    inner class DelegatedSessionAuthorizationContext(override val session: ISession, override val tenant: ITenant?, override val token: IJWT) : IDelegatedSessionAuthorizationContext {
        override val scopes: Set<IScope>
            get() = token.scope()?.split(" ")?.mapNotNull(::findScopeById)?.toSet() ?: setOf()

        override val roles: Set<IRole>
            get() {
                val platformRoles = account.roles

                val tenantRoles = if (tenant == null) setOf()
                else tenantMembershipRepository.findByAccount(account)
                    .filter { it.tenant.id == tenant.id }
                    .flatMap { it.roles }

                return platformRoles + tenantRoles
            }
    }

    inner class ServiceSessionAuthorizationContext(override val client: IClient, override val token: IJWT) : IServiceSessionAuthorizationContext {
        override val scopes: Set<IScope>
            get() = token.scope()?.split(" ")?.mapNotNull(::findScopeById)?.toSet() ?: setOf()

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

            if (session.verify(sessionToken) && session.expiresAt > Instant.now())
                return SessionAuthorizationContext(session)

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

        val decodedToken = jwtService.decode(token)

        sessionId = decodedToken.claim("https://id.jasmine.software/session_id")?.let {
            runCatching { UUID.fromString(it.asString) }.getOrNull()
        }

        if (sessionId == null) {
            val clientId = decodedToken.subject()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return null

            val client = clientRepository.findById(clientId)
                ?: return null

            return ServiceSessionAuthorizationContext(client, decodedToken)
        }

        val session = sessionRepository.findById(sessionId)
            ?: return null

        if (session.expiresAt < Instant.now())
            return null

        val tenantId = decodedToken.claim("https://id.jasmine.software/tenant_id")?.let {
            runCatching { UUID.fromString(it.asString) }.getOrNull()
        }

        val tenant = tenantId?.let { tenantRepository.findById(it) }

        return DelegatedSessionAuthorizationContext(session, tenant, decodedToken)
    }
}

sealed class InterfacePolicy : Policy<IAuthorizationContext> {
    class HasPrivilege(val privilege: IPrivilege) : InterfacePolicy() {
        override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
            if (context.privileges.contains(privilege))
                return PolicyResult.Pass(this, context)

            return PolicyResult.Fail()
        }
    }

    class HasScope(val scope: IScope) : InterfacePolicy() {
        override fun evaluate(context: IAuthorizationContext): PolicyResult<IAuthorizationContext> {
            if (context !is IScopedAuthorizationContext)
                return PolicyResult.Pass(this, context)

            if (context.scopes.any { it.id == scope.id })
                return PolicyResult.Pass(this, context)

            return PolicyResult.Fail()
        }
    }
}
