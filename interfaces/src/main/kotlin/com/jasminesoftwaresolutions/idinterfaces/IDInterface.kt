package com.jasminesoftwaresolutions.idinterfaces

import com.jasminesoftwaresolutions.id.domain.IDServer
import com.jasminesoftwaresolutions.idinterfaces.services.JavalinAuthorizationService
import io.javalin.Javalin
import io.javalin.config.JavalinConfig

interface IDInterface

abstract class IDJavalinInterface(protected val server: IDServer) : IDInterface {
    protected var authorizationService
        = JavalinAuthorizationService(server.sessionRepository, server.clientRepository, server.tenantRepository, server.tenantMembershipRepository, server.tokenService, server.scopeRegistry, server.scopeRepository)

    abstract fun install(config: JavalinConfig)
    abstract fun install(app: Javalin)
}
