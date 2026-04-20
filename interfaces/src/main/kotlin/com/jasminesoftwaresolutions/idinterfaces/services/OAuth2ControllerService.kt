package com.jasminesoftwaresolutions.idinterfaces.services

import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.PlatformScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.StaticScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.IEncryptionFunction
import java.time.Instant
import java.util.*

open class OAuth2ControllerService(
    protected val sessionRepository: ISessionRepository<out ISession>,
    protected val clientRepository: IClientRepository<out IClient>,
    protected val tenantRepository: ITenantRepository<out ITenant>,
    protected val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>,
    protected val delegatedSessionRepository: IDelegatedSessionRepository<out IDelegatedSession>,
    protected val encryptionFunction: IEncryptionFunction
) {
    open class OAuth2Request(
        val clientId: UUID,
        val redirectUri: String,
        val scope: String? = null,
        val state: String? = null
    )

    class TenantOAuth2Request(
        clientId: UUID,
        redirectUri: String,
        scope: String? = null,
        state: String? = null,
        val tenantId: UUID? = null
    ) : OAuth2Request(clientId, redirectUri, scope, state)

    open inner class AuthorizeResult()
    inner class InvalidClientAuthorizeResult : AuthorizeResult()
    inner class InvalidRedirectUriAuthorizeResult : AuthorizeResult()
    inner class LoginRequiredAuthorizeResult : AuthorizeResult()
    inner class InvalidTenantAuthorizeResult : AuthorizeResult()
    inner class SelectTenantAuthorizeResult(val account: IAccount, val tenants: List<ITenant>) : AuthorizeResult()
    inner class ConsentRequiredAuthorizeResult(val client: IClient, val account: IAccount, val tenant: ITenant?, val scopes: List<IScope>) : AuthorizeResult()

    protected fun getSession(id: UUID, token: String)
        = sessionRepository.findById(id)?.takeIf { it.verify(token) }

    open fun authorize(
        sessionId: UUID,
        sessionToken: String,
        request: OAuth2Request
    ) : AuthorizeResult {
        val client = clientRepository.findById(request.clientId)
            ?: return InvalidClientAuthorizeResult()

        val redirectUri = client.redirectUris.values.firstOrNull { it.toString() == request.redirectUri }
            ?: return InvalidRedirectUriAuthorizeResult()

        val session = getSession(sessionId, sessionToken)
        if (session == null || Instant.now() > session.expiresAt)
            return LoginRequiredAuthorizeResult()

        val linkedTenants = tenantMembershipRepository.findByAccount(session.account.id)
            .map { it.tenant }

        if (request is TenantOAuth2Request && request.tenantId == null) {
            return SelectTenantAuthorizeResult(
                account = session.account,
                tenants = linkedTenants
            )
        }

        val tenant = (request as? TenantOAuth2Request)?.tenantId
            ?.let { tenantRepository.findById(it)
                ?: return InvalidTenantAuthorizeResult() }

        if (tenant != null && tenant.id !in linkedTenants.map(ITenant::id))
            return InvalidTenantAuthorizeResult()

        val scopes = mutableListOf<IScope>()
        for (scope in request.scope?.split(" ") ?: emptyList())
            StaticScope.all().firstOrNull { it.id == scope }?.let { scopes.add(it) }

        return ConsentRequiredAuthorizeResult(
            client, session.account, tenant, scopes
        )
    }

    open inner class ConsentResult()
    inner class InvalidConsentResult : ConsentResult()
    inner class LoginRequiredConsentResult : ConsentResult()
    inner class ConsentedConsentResult(val redirectUri: String) : ConsentResult()

    open fun consent(
        sessionId: UUID,
        sessionToken: String,
        request: OAuth2Request
    ) : ConsentResult {
        val session = getSession(sessionId, sessionToken)
        if (session == null || Instant.now() > session.expiresAt)
            return LoginRequiredConsentResult()

        val tenant = (request as? TenantOAuth2Request)?.tenantId
            ?.let { tenantRepository.findById(it)
                ?: return InvalidConsentResult() }

        val client = clientRepository.findById(request.clientId)
            ?: return InvalidConsentResult()

        val redirectUri = client.redirectUris.values.firstOrNull { it.toString() == request.redirectUri }
            ?: return InvalidConsentResult()

        val scopes = request.scope
            ?.split(" ")
            ?.mapNotNull { scope -> StaticScope.all().firstOrNull { it.id == scope } }
            ?.toMutableList() ?: mutableListOf<IScope>()

        if (tenant == null)
            scopes.removeIf { it !is PlatformScope }

        val code = SecureToken()
        val delegatedSession = delegatedSessionRepository.create {
            this.expiresAt = Instant.now().plusSeconds(60)

            this.tenant = tenant
            this.client = client
            this.session = session

            this.redirectUri = redirectUri
            this.scope = scopes.map { it.id }.joinToString(" ")

            this.code = code
        }

        var redirect = redirectUri.toString() + "?code=" + code

        if (request.state != null)
            redirect += "&state=${request.state}"

        if (scopes.isNotEmpty())
            redirect += "&scope=" + scopes.map { it.id }.joinToString(" ")

        return ConsentedConsentResult(redirect)
    }
}