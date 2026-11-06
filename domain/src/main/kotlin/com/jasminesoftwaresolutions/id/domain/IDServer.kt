package com.jasminesoftwaresolutions.id.domain

import com.google.gson.Gson
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.handoff.IHandoffQrTokenRepository
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepHandlerRegistry
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepRegistry
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.IEncryptionFunction
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import com.jasminesoftwaresolutions.id.domain.services.ISigningFunction
import com.jasminesoftwaresolutions.id.domain.services.accounts.IEmailChangeService
import com.jasminesoftwaresolutions.id.domain.services.accounts.IJWTService
import com.jasminesoftwaresolutions.id.domain.services.accounts.IMagicLinkService
import com.jasminesoftwaresolutions.id.domain.services.accounts.ISessionService
import com.jasminesoftwaresolutions.id.domain.services.accounts.ITOTPService
import com.jasminesoftwaresolutions.id.domain.services.authentication.IAuthenticationService
import com.jasminesoftwaresolutions.id.domain.services.handoff.IHandoffService
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.services.authorization.IOIDCClaimsResolver
import com.jasminesoftwaresolutions.id.domain.services.authorization.ITokenService
import com.jasminesoftwaresolutions.id.domain.services.security.IRestrictionService

interface IDServer {
    var gson: Gson
    var encryptionFunction: IEncryptionFunction
    var hashFunction: IHashFunction
    var signingFunction: ISigningFunction

    var authenticationStepRegistry: IAuthenticationStepRegistry
    var authenticationStepHandlerRegistry: IAuthenticationStepHandlerRegistry
    var scopeRegistry: IScopeRegistry<IScope>

    var accountRepository: IAccountRepository
    var passwordRepository: IPasswordRepository
    var magicLinkRepository: IMagicLinkRepository
    var emailChangeRepository: IEmailChangeRepository
    var totpConfigurationRepository: ITOTPConfigurationRepository
    var sessionRepository: ISessionRepository

    var authenticationFlowRepository: IAuthenticationFlowRepository

    var clientRepository: IClientRepository
    var delegatedSessionRepository: IDelegatedSessionRepository
    var scopeRepository: IScopeRepository

    var handoffRepository: IHandoffRepository
    var handoffQrTokenRepository: IHandoffQrTokenRepository

    var roleRepository: IRoleRepository
    var roleAssignmentRepository: IRoleAssignmentRepository

    var policyRepository: IPolicyRepository
    var policyAssignmentRepository: IPolicyAssignmentRepository

    var tenantRepository: ITenantRepository
    var tenantMembershipRepository: ITenantMembershipRepository
    var membershipInvitationRepository: IMembershipInvitationRepository

    var eventDispatcher: IEventDispatcher
    var authorizationService: IAuthorizationService

    var jwtService: IJWTService
    var magicLinkService: IMagicLinkService
    var emailChangeService: IEmailChangeService
    var sessionService: ISessionService
    var totpService: ITOTPService
    var handoffService: IHandoffService

    var authenticationService: IAuthenticationService
    var oidcClaimsResolver: IOIDCClaimsResolver
    var tokenService: ITokenService<IAccessToken, IRefreshToken, IServiceToken, IIDToken>

    var restrictionRepository: IRestrictionRepository
    var restrictionService: IRestrictionService

    fun start(port: Int)
    fun stop()
}