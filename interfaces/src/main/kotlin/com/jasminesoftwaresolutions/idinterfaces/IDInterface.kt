package com.jasminesoftwaresolutions.idinterfaces

import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.idinterfaces.services.LoginControllerService
import io.javalin.Javalin
import io.javalin.config.JavalinConfig

interface IDInterface {
    var loginControllerService: LoginControllerService<IAuthenticationFlow>
}

interface IDJavalinInterface : IDInterface {
    fun install(config: JavalinConfig)
    fun install(app: Javalin)
}