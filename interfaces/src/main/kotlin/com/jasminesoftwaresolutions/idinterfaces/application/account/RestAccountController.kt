package com.jasminesoftwaresolutions.idinterfaces.application.account

import com.fasterxml.jackson.annotation.JsonProperty
import com.jasminesoftwaresolutions.idinterfaces.services.AccountControllerService
import com.jasminesoftwaresolutions.idinterfaces.services.JavalinAuthorizationService
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Post
import io.javalin.http.*
import java.util.*

class RestAccountController(
    private val service: AccountControllerService,
    private val authorizationService: JavalinAuthorizationService
) {
    data class CreateAccountPayload(
        @get:JsonProperty("email") val email: String?,
        @get:JsonProperty("first_name") val firstName: String?,
        @get:JsonProperty("last_name") val lastName: String?,
    )

    @Get("/api/v1/accounts/me")
    fun me(ctx: Context) {
        val authentication = authorizationService.authenticate(ctx)
            ?: throw UnauthorizedResponse()

        when (val result = service.getMe(authentication)) {
            is AccountControllerService.GetAccountResult.Success -> ctx.json(result.account)
            is AccountControllerService.GetAccountResult.Forbidden -> throw ForbiddenResponse()
            is AccountControllerService.GetAccountResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Get("/api/v1/accounts/{id}")
    fun getById(ctx: Context) {
        val authentication = authorizationService.authenticate(ctx)
            ?: throw UnauthorizedResponse()

        val accountId = runCatching { UUID.fromString(ctx.pathParam("id")) }.getOrNull()
            ?: throw BadRequestResponse("Invalid account id")

        when (val result = service.getById(authentication, accountId)) {
            is AccountControllerService.GetAccountResult.Success -> ctx.json(result.account)
            is AccountControllerService.GetAccountResult.Forbidden -> throw ForbiddenResponse()
            is AccountControllerService.GetAccountResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/api/v1/accounts")
    fun create(ctx: Context) {
        val authentication = authorizationService.authenticate(ctx)
            ?: throw UnauthorizedResponse()

        val payload = runCatching { ctx.bodyAsClass(CreateAccountPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val email = payload.email?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing email")
        val firstName = payload.firstName?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing first_name")
        val lastName = payload.lastName?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing last_name")

        when (val result = service.create(authentication, AccountControllerService.CreateAccountRequest(email, firstName, lastName))) {
            is AccountControllerService.CreateAccountResult.Success -> {
                ctx.status(if (result.created) 201 else 200)
                ctx.json(result.account)
            }

            is AccountControllerService.CreateAccountResult.Forbidden -> throw ForbiddenResponse()
        }
    }
}
