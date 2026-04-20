package com.jasminesoftwaresolutions.idinterfaces

import io.javalin.Javalin
import io.javalin.config.JavalinConfig

interface IDInterface

interface IDJavalinInterface : IDInterface {
    fun install(config: JavalinConfig)
    fun install(app: Javalin)
}