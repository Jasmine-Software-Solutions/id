package com.jasminesoftwaresolutions.idinterfaces.application

import com.jasminesoftwaresolutions.id.domain.IDServer
import com.jasminesoftwaresolutions.idinterfaces.IDJavalinInterface
import com.jasminesoftwaresolutions.idinterfaces.application.account.RestAccountController
import com.jasminesoftwaresolutions.idinterfaces.application.authorization.RestClientScopesControllerService
import com.jasminesoftwaresolutions.idinterfaces.application.authorization.RestOAuth2Controller
import com.jasminesoftwaresolutions.idinterfaces.services.AccountControllerService
import com.jasminesoftwaresolutions.idinterfaces.services.ClientScopesControllerService
import com.jasminesoftwaresolutions.idinterfaces.services.OAuth2ControllerService
import io.javalin.Javalin
import io.javalin.community.routing.annotations.AnnotatedRouting
import io.javalin.config.JavalinConfig
import java.util.*

open class IDJavalinApplicationInterface(server: IDServer) : IDJavalinInterface(server) {
    var oAuth2ControllerService
        = OAuth2ControllerService(server.sessionRepository, server.clientRepository, server.tenantRepository, server.tenantMembershipRepository, server.delegatedSessionRepository, server.encryptionFunction, server.tokenService, authorizationService, server.scopeRegistry, server.scopeRepository)

    var clientScopesControllerService
        = ClientScopesControllerService(authorizationService, server.clientRepository, server.scopeRepository, server.scopeRegistry)

    var accountControllerService
        = AccountControllerService(server.accountRepository, server.tenantMembershipRepository)

    override fun install(config: JavalinConfig) {
        val oauth2Controller = RestOAuth2Controller(oAuth2ControllerService, authorizationService)
        val clientScopesController = RestClientScopesControllerService(clientScopesControllerService, authorizationService)
        val accountController = RestAccountController(accountControllerService, authorizationService)

        config.router.mount(AnnotatedRouting) {
            it.registerEndpoints(
                oauth2Controller,
                clientScopesController,
                accountController
            )
        }

        config.validation.register(UUID::class.java, UUID::fromString)
    }

    override fun install(app: Javalin) {

    }
}
