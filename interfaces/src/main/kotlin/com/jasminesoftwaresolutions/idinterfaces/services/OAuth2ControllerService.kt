package com.jasminesoftwaresolutions.idinterfaces.services

import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.IEncryptionFunction
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.services.authorization.ITokenService
import java.time.Instant
import java.util.*

open class OAuth2ControllerService(
    protected val sessionRepository: ISessionRepository<out ISession>,
    protected val clientRepository: IClientRepository<out IClient>,
    protected val tenantRepository: ITenantRepository<out ITenant>,
    protected val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>,
    protected val delegatedSessionRepository: IDelegatedSessionRepository<out IDelegatedSession>,
    protected val encryptionFunction: IEncryptionFunction,
    protected val tokenService: ITokenService<out IDelegatedSessionAccessToken, out IDelegatedSessionRefreshToken, out IServiceSessionAccessToken>,
    protected val authorizationService: IAuthorizationService<*, out IAuthorizationContext>,
    protected val scopeRegistry: IScopeRegistry<out IScope>,
    protected val scopeRepository: IScopeRepository<out IRegisteredScope>,
) {
    private fun findScopeById(id: String): IScope? =
        scopeRegistry.findById(id) ?: scopeRepository.findById(id)

    private fun parseRequestedScopes(scope: String?): MutableList<IScope> {
        return scope
            ?.split(" ")
            ?.mapNotNull(::findScopeById)
            ?.distinctBy { it.id }
            ?.toMutableList()
            ?: mutableListOf()
    }

    private fun allKnownScopes(): Set<IScope> =
        scopeRegistry.entries() + scopeRepository.entries()

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

    open class AuthorizeResult {
        class Unauthorized : AuthorizeResult()
        class InvalidClient : AuthorizeResult()
        class InvalidRedirectUri : AuthorizeResult()
        class InvalidTenant : AuthorizeResult()
        class SelectTenant(val account: IAccount, val tenants: List<ITenant>) : AuthorizeResult()
        class ConsentRequired(val client: IClient, val account: IAccount, val tenant: ITenant?, val scopes: List<IScope>) : AuthorizeResult()
        class Granted(val redirectUri: String) : AuthorizeResult()
    }

    open fun authorize(
        authentication: IAuthorizationContext,
        request: OAuth2Request
    ) : AuthorizeResult {
        if (authentication !is ISessionAuthorizationContext)
            return AuthorizeResult.Unauthorized()

        val client = clientRepository.findById(request.clientId)
            ?: return AuthorizeResult.InvalidClient()

        val redirectUri = client.redirectUris.values.firstOrNull { it.toString() == request.redirectUri }
            ?: return AuthorizeResult.InvalidRedirectUri()

        val linkedTenants = tenantMembershipRepository.findByAccount(authentication.session.account.id)
            .map { it.tenant }

        if (request is TenantOAuth2Request && request.tenantId == null) {
            return AuthorizeResult.SelectTenant(
                account = authentication.session.account,
                tenants = linkedTenants
            )
        }

        val tenant = (request as? TenantOAuth2Request)?.tenantId
            ?.let { tenantRepository.findById(it)
                ?: return AuthorizeResult.InvalidTenant()
            }

        if (tenant != null && tenant.id !in linkedTenants.map(ITenant::id))
            return AuthorizeResult.InvalidTenant()

        val scopes = parseRequestedScopes(request.scope)

        if (client.roles.flatMap { it.privileges }.contains(OAuth2ImplicitConsentPrivilege)) {
            val consentResult = consent(authentication, request)
            if (consentResult is ConsentResult.Consented) {
                val redirectUri = consentResult.redirectUri
                return AuthorizeResult.Granted(redirectUri)
            }
        }

        return AuthorizeResult.ConsentRequired(
            client, authentication.session.account, tenant, scopes
        )
    }

    open class ConsentResult {
        class Unauthorized : ConsentResult()
        class Invalid : ConsentResult()
        class Consented(val redirectUri: String) : ConsentResult()
    }

    open fun consent(
        authentication: IAuthorizationContext,
        request: OAuth2Request
    ) : ConsentResult {
        if (authentication !is ISessionAuthorizationContext)
            return ConsentResult.Unauthorized()

        val tenant = (request as? TenantOAuth2Request)?.tenantId
            ?.let { tenantRepository.findById(it)
                ?: return ConsentResult.Invalid() }

        val client = clientRepository.findById(request.clientId)
            ?: return ConsentResult.Invalid()

        val redirectUri = client.redirectUris.values.firstOrNull { it.toString() == request.redirectUri }
            ?: return ConsentResult.Invalid()

        val scopes = parseRequestedScopes(request.scope)

        if (tenant == null)
            scopes.removeIf { it is TenantScope }

        val code = SecureToken()
        val delegatedSession = delegatedSessionRepository.create {
            this.expiresAt = Instant.now().plusSeconds(60)

            this.tenant = tenant
            this.client = client
            this.session = authentication.session

            this.redirectUri = redirectUri
            this.scopes = scopes.toSet()

            this.code.code = code
        }

        val encodedCode = Base64.getEncoder().encodeToString("${delegatedSession.id}:${delegatedSession.code}".toByteArray())
        var redirect = redirectUri.toString() + "?code=" + encodedCode

        if (request.state != null)
            redirect += "&state=${request.state}"

        if (scopes.isNotEmpty())
            redirect += "&scope=" + scopes.map { it.id }.joinToString(" ")

        return ConsentResult.Consented(redirect)
    }

    open class TokenWithCodeResult {
        class InvalidClient : TokenWithCodeResult()
        class Unauthorized : TokenWithCodeResult()
        class InvalidRedirectUri : TokenWithCodeResult()
        class InvalidCode : TokenWithCodeResult()
        class Granted(val accessToken: String, val refreshToken: String, val expiresIn: Long, val refreshTokenExpiresIn: Long) : TokenWithCodeResult()
    }

    open fun getTokenWithCode(
        authentication: IAuthorizationContext,
        clientId: UUID,
        redirectUri: String,
        code: String
    ) : TokenWithCodeResult {
        if (authentication !is IClientAuthorizationContext)
            return TokenWithCodeResult.Unauthorized()

        if (authentication.client.id != clientId)
            return TokenWithCodeResult.InvalidClient()

        val client = clientRepository.findById(clientId)
            ?: return TokenWithCodeResult.InvalidClient()

        val redirectUri = client.redirectUris.values.firstOrNull { it.toString() == redirectUri }
            ?: return TokenWithCodeResult.InvalidRedirectUri()

        val decodedCode = Base64.getDecoder().decode(code).toString(Charsets.UTF_8).split(":")
        if (decodedCode.size != 2)
            return TokenWithCodeResult.InvalidCode()

        val delegatedSessionId = runCatching { UUID.fromString(decodedCode[0]) }.getOrNull()
            ?: return TokenWithCodeResult.InvalidCode()

        val delegatedSessionCode = decodedCode[1]

        val delegatedSession = delegatedSessionRepository.findById(delegatedSessionId)
            ?: return TokenWithCodeResult.InvalidCode()

        if (!delegatedSession.code.verify(delegatedSessionCode))
            return TokenWithCodeResult.InvalidCode()

        val accessToken = tokenService.generateDelegatedSessionAccessToken(delegatedSession)
            ?: return TokenWithCodeResult.Unauthorized()

        val refreshToken = tokenService.generateDelegatedSessionRefreshToken(delegatedSession)
            ?: return TokenWithCodeResult.Unauthorized()

        return TokenWithCodeResult.Granted(
            accessToken = accessToken.token,
            refreshToken = refreshToken.token,
            expiresIn = accessToken.expiresAt.epochSecond - Instant.now().epochSecond,
            refreshTokenExpiresIn = delegatedSession.session.expiresAt.epochSecond - Instant.now().epochSecond
        )
    }

    open class TokenWithCredentialsResult {
        class Unauthorized : TokenWithCredentialsResult()
        class InvalidClient : TokenWithCredentialsResult()
        class InvalidCredentials : TokenWithCredentialsResult()
        class Granted(val accessToken: String, val expiresIn: Long) : TokenWithCredentialsResult()
    }

    open fun getTokenWithCredentials(
        authentication: IAuthorizationContext,
        tenantId: UUID?,
        scope: String?
    ) : TokenWithCredentialsResult {
        if (authentication !is IClientAuthorizationContext || authentication is IScopedAuthorizationContext)
            return TokenWithCredentialsResult.Unauthorized()

        val tenant = tenantId?.let { tenantRepository.findById(it) }

        val scopes = if (scope != null) {
            scope.split(" ").mapNotNull(::findScopeById).toSet()
        } else null

        val accessToken = tokenService.generateServiceSessionAccessToken(
            authentication.client,
            tenant,
            scopes
        ) ?: return TokenWithCredentialsResult.Unauthorized()

        return TokenWithCredentialsResult.Granted(
            accessToken = accessToken.token,
            expiresIn = accessToken.expiresAt.epochSecond - Instant.now().epochSecond
        )
    }
}
