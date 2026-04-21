package com.jasminesoftwaresolutions.idinterfaces.user

import com.jasminesoftwaresolutions.id.domain.IDServer
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.registries.AuthenticationStepRendererRegistry
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterEmailAddressAuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterPasswordAuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterTOTPAuthenticationFlowStep
import com.jasminesoftwaresolutions.idinterfaces.IDJavalinInterface
import com.jasminesoftwaresolutions.idinterfaces.services.LoginControllerService
import com.jasminesoftwaresolutions.idinterfaces.services.OAuth2ControllerService
import com.jasminesoftwaresolutions.idinterfaces.user.authentication.AjaxLoginController
import com.jasminesoftwaresolutions.idinterfaces.user.authentication.renderers.HTMLEnterEmailAddressAuthenticationFlowRenderer
import com.jasminesoftwaresolutions.idinterfaces.user.authentication.renderers.HTMLEnterPasswordAuthenticationFlowRenderer
import com.jasminesoftwaresolutions.idinterfaces.user.authentication.renderers.HTMLEnterTOTPAuthenticationFlowRenderer
import com.jasminesoftwaresolutions.idinterfaces.user.authorization.AjaxOAuth2Controller
import io.javalin.Javalin
import io.javalin.community.routing.annotations.AnnotatedRouting
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import java.util.*

open class IDJavalinUserInterface(server: IDServer) : IDJavalinInterface(server) {
    var loginControllerService
        = LoginControllerService(server.authenticationStepRegistry, server.authenticationFlowRepository, server.authenticationService, server.signingFunction)

    var oAuth2ControllerService
        = OAuth2ControllerService(server.sessionRepository, server.clientRepository, server.tenantRepository, server.tenantMembershipRepository, server.delegatedSessionRepository, server.encryptionFunction, server.jwtService, authorizationService, server.scopeRegistry)

    open var authenticationStepRendererRegistry = AuthenticationStepRendererRegistry<IAuthenticationFlow>().apply {
        register(
            agentType = Context::class,
            step = EnterEmailAddressAuthenticationFlowStep,
            renderer = HTMLEnterEmailAddressAuthenticationFlowRenderer()
        )

        register(
            agentType = Context::class,
            step = EnterPasswordAuthenticationFlowStep,
            renderer = HTMLEnterPasswordAuthenticationFlowRenderer()
        )

        register(
            agentType = Context::class,
            step = EnterTOTPAuthenticationFlowStep,
            renderer = HTMLEnterTOTPAuthenticationFlowRenderer()
        )
    }

    override fun install(config: JavalinConfig) {
        val loginController = AjaxLoginController(authenticationStepRendererRegistry, loginControllerService)
        val oauth2Controller = AjaxOAuth2Controller(oAuth2ControllerService, authorizationService)

        config.router.mount(AnnotatedRouting) {
            it.registerEndpoints(
                loginController,
                oauth2Controller
            )
        }

        config.validation.register(UUID::class.java, UUID::fromString)
    }

    override fun install(app: Javalin) {

    }
}