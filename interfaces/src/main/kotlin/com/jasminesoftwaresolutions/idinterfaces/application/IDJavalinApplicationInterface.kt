package com.jasminesoftwaresolutions.idinterfaces.application

import com.jasminesoftwaresolutions.id.domain.IDServer
import com.jasminesoftwaresolutions.idinterfaces.IDJavalinInterface
import com.jasminesoftwaresolutions.idinterfaces.application.account.RestAccountController
import com.jasminesoftwaresolutions.idinterfaces.application.account.RestAdminAccountController
import com.jasminesoftwaresolutions.idinterfaces.application.auth.RestAuthController
import com.jasminesoftwaresolutions.idinterfaces.application.authorization.RestClientAdminController
import com.jasminesoftwaresolutions.idinterfaces.application.authorization.RestClientScopesController
import com.jasminesoftwaresolutions.idinterfaces.application.authorization.RestOAuth2Controller
import com.jasminesoftwaresolutions.idinterfaces.application.authorization.RestPolicyController
import com.jasminesoftwaresolutions.idinterfaces.application.authorization.RestRoleController
import com.jasminesoftwaresolutions.idinterfaces.application.handoff.HandoffExchangeGuard
import com.jasminesoftwaresolutions.idinterfaces.application.handoff.RestHandoffController
import com.jasminesoftwaresolutions.idinterfaces.application.tenant.RestMembershipController
import com.jasminesoftwaresolutions.idinterfaces.application.tenant.RestMembershipInvitationController
import com.jasminesoftwaresolutions.idinterfaces.application.tenant.RestTenantController
import com.jasminesoftwaresolutions.idinterfaces.application.security.RestRestrictionController
import com.jasminesoftwaresolutions.idinterfaces.services.security.JavalinRestrictionService
import io.javalin.Javalin
import io.javalin.community.routing.annotations.AnnotatedRouting
import io.javalin.config.JavalinConfig
import io.javalin.http.ForbiddenResponse
import java.util.*

open class IDJavalinApplicationInterface(server: IDServer, private val externalBaseUrl: String) :
    IDJavalinInterface(server) {

    override fun install(config: JavalinConfig) {
        val restAuthController = RestAuthController(
            server.authenticationService,
            server.authenticationFlowRepository,
            server.authenticationStepRegistry,
            server.magicLinkRepository,
            server.magicLinkService,
            server.signingFunction,
            server.jwtService,
            server.eventDispatcher,
        )

        val restAccountController = RestAccountController(
            server.accountRepository,
            server.sessionRepository,
            server.passwordRepository,
            server.totpService,
            server.totpConfigurationRepository,
            server.roleRepository,
            server.roleAssignmentRepository,
            server.tenantRepository,
            server.tenantMembershipRepository,
            server.policyAssignmentRepository,
            server.membershipInvitationRepository,
            server.tenantMembershipRepository,
            authorizationService,
            server.authorizationService,
            scopedListHelper,
            server.clientRepository,
            server.emailChangeService,
            server.emailChangeRepository,
            externalBaseUrl,
            server.eventDispatcher,
        )

        val handoffExchangeGuard = HandoffExchangeGuard(
            server.handoffRepository,
            server.authorizationService,
        )

        val restOAuth2Controller = RestOAuth2Controller(
            server.clientRepository,
            server.delegatedSessionRepository,
            server.encryptionFunction,
            server.tokenService,
            server.scopeRegistry,
            server.scopeRepository,
            authorizationService,
            server.authorizationService,
            scopedListHelper,
            server.oidcClaimsResolver,
            externalBaseUrl,
            handoffExchangeGuard,
        )

        val restHandoffController = RestHandoffController(
            server.handoffRepository,
            server.handoffService,
            server.clientRepository,
            server.scopeRegistry,
            server.scopeRepository,
            authorizationService,
            server.authorizationService,
            externalBaseUrl,
        )

        val restClientScopesController = RestClientScopesController(
            server.clientRepository,
            server.scopeRepository,
            server.scopeRegistry,
            authorizationService,
            server.authorizationService,
        )

        val restRoleController = RestRoleController(
            server.roleRepository,
            server.roleAssignmentRepository,
            server.tenantRepository,
            server.accountRepository,
            server.clientRepository,
            authorizationService,
            server.authorizationService,
            scopedListHelper,
            server.eventDispatcher,
        )

        val restPolicyController = RestPolicyController(
            server.policyRepository,
            server.policyAssignmentRepository,
            server.tenantRepository,
            server.accountRepository,
            server.clientRepository,
            server.roleRepository,
            authorizationService,
            server.authorizationService,
            scopedListHelper,
            server.eventDispatcher,
        )

        val restMembershipController = RestMembershipController(
            server.tenantMembershipRepository,
            server.tenantRepository,
            server.accountRepository,
            authorizationService,
            server.authorizationService,
            server.eventDispatcher,
        )

        val restClientAdminController = RestClientAdminController(
            server.clientRepository,
            server.hashFunction,
            authorizationService,
            server.authorizationService,
            server.eventDispatcher,
        )

        val restTenantController = RestTenantController(
            server.tenantRepository,
            authorizationService,
            server.authorizationService,
            scopedListHelper,
            server.eventDispatcher,
        )

        val restMembershipInvitationController = RestMembershipInvitationController(
            server.membershipInvitationRepository,
            server.tenantRepository,
            server.accountRepository,
            server.tenantMembershipRepository,
            authorizationService,
            server.authorizationService,
            server.eventDispatcher,
        )

        val restAdminAccountController = RestAdminAccountController(
            server.accountRepository,
            server.tenantMembershipRepository,
            server.totpConfigurationRepository,
            server.totpService,
            authorizationService,
            server.authorizationService,
            server.eventDispatcher,
        )

        val restRestrictionController = RestRestrictionController(
            server.restrictionService,
            server.authorizationService,
            authorizationService,
            server.accountRepository
        )

        config.router.mount(AnnotatedRouting) {
            it.registerEndpoints(
                restAuthController,
                restAccountController,
                restOAuth2Controller,
                restHandoffController,
                restClientScopesController,
                restClientAdminController,
                restRoleController,
                restPolicyController,
                restMembershipController,
                restAdminAccountController,
                restTenantController,
                restMembershipInvitationController,
                restRestrictionController,
            )
        }

        config.validation.register(UUID::class.java, UUID::fromString)
    }

    override fun install(app: Javalin) {
        val javalinRestrictionService = JavalinRestrictionService(
            server.restrictionService,
            authorizationService,
        )

        app.before { ctx ->
            if (javalinRestrictionService.restrictRequest(ctx)) {
                ctx.header("X-ID-Restricted", "true")
                throw ForbiddenResponse("Access restricted")
            }
        }
    }
}
