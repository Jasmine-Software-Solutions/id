package com.jasminesoftwaresolutions.idinterfaces

import com.jasminesoftwaresolutions.id.domain.IDServer
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.ScopedListHelper
import io.javalin.Javalin
import io.javalin.config.JavalinConfig

interface IDInterface

abstract class IDJavalinInterface(protected val server: IDServer) : IDInterface {
    protected var authorizationService
        = JavalinRequestAuthenticationService(server.sessionRepository, server.clientRepository, server.tokenService)

    protected val scopedListHelper = ScopedListHelper(server.authorizationService)

    abstract fun install(config: JavalinConfig)
    abstract fun install(app: Javalin)
}
