package com.jasminesoftwaresolutions.id.domain

import com.google.gson.Gson
import com.jasminesoftwaresolutions.id.domain.models.account.*
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepHandlerRegistry
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepRegistry
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.IEmailService
import com.jasminesoftwaresolutions.id.domain.services.IEncryptionFunction
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import com.jasminesoftwaresolutions.id.domain.services.ISigningFunction
import com.jasminesoftwaresolutions.id.domain.services.accounts.IJWTService
import com.jasminesoftwaresolutions.id.domain.services.accounts.IMagicLinkService
import com.jasminesoftwaresolutions.id.domain.services.accounts.ISessionService
import com.jasminesoftwaresolutions.id.domain.services.accounts.ITOTPService
import com.jasminesoftwaresolutions.id.domain.services.authentication.IAuthenticationService
import com.jasminesoftwaresolutions.id.domain.services.authorization.ITokenService

interface IDServer {
    var gson: Gson
    var encryptionFunction: IEncryptionFunction
    var hashFunction: IHashFunction
    var signingFunction: ISigningFunction

    var authenticationStepRegistry: IAuthenticationStepRegistry<IAuthenticationFlow>
    var authenticationStepHandlerRegistry: IAuthenticationStepHandlerRegistry<IAuthenticationFlow>
    var scopeRegistry: IScopeRegistry<IScope>

    var accountRepository: IAccountRepository<IAccount>
    var passwordRepository: IPasswordRepository<IHashedPassword>
    var magicLinkRepository: IMagicLinkRepository<IHashedMagicLink>
    var totpConfigurationRepository: ITOTPConfigurationRepository<ITOTPConfiguration>
    var sessionRepository: ISessionRepository<IHashedSession>

    var authenticationFlowRepository: IAuthenticationFlowRepository<IAuthenticationFlow>

    var clientRepository: IClientRepository<IClient>
    var delegatedSessionRepository: IDelegatedSessionRepository<IDelegatedSession>
    var scopeRepository: IScopeRepository<IRegisteredScope>

    var tenantRepository: ITenantRepository<ITenant>
    var tenantMembershipRepository: ITenantMembershipRepository<ITenantMembership>

    var emailService: IEmailService

    var jwtService: IJWTService
    var magicLinkService: IMagicLinkService<IMagicLink>
    var sessionService: ISessionService<ISession>
    var totpService: ITOTPService<ISetTOTPConfiguration>

    var authenticationService: IAuthenticationService<IAuthenticationFlow>
    var tokenService: ITokenService<IDelegatedSessionAccessToken, IDelegatedSessionRefreshToken, IServiceSessionAccessToken>

    fun start(port: Int)
    fun stop()
}
