package app.controllers

import app.application.accounts.*
import app.infrastructure.etc.toUUIDOrNull
import com.fasterxml.jackson.annotation.JsonProperty
import io.javalin.community.routing.annotations.*
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.time.Instant
import java.util.*

@Endpoints("/api/v1/accounts")
class RestAccountsController(
    private val listAccountsHandler: ListAccountsHandler,
    private val getAccountHandler: GetAccountHandler,
    private val createAccountHandler: CreateAccountHandler,
) {
    data class CreateAccountPayload(
        @field:JsonProperty("email") val email: String?,
        @field:JsonProperty("first_name") val firstName: String?,
        @field:JsonProperty("last_name") val lastName: String?,
    )

    @Get
    fun list(ctx: Context, @Header("Authorization") authorization: String?) {
        val tenant = ctx.queryParam("tenant")?.toUUIDOrNull()

        val command = ListAccountsCommand(
            authorization,
            tenant,
            id = ctx.queryParam("id")?.let { UUID.fromString(it) },
            email = ctx.queryParam("email"),
            firstName = ctx.queryParam("firstName"),
            lastName = ctx.queryParam("lastName"),
            createdAtBefore = ctx.queryParam("createdAt.before")?.toLongOrNull()?.let { Instant.ofEpochMilli(it) },
            createdAtAfter = ctx.queryParam("createdAt.after")?.toLongOrNull()?.let { Instant.ofEpochMilli(it) },
            createdAtExact = ctx.queryParam("createdAt.exact")?.toLongOrNull()?.let { Instant.ofEpochMilli(it) },
            matchAny = ctx.queryParam("any")?.toBooleanStrictOrNull() ?: false,
        )

        when (val result = listAccountsHandler.execute(command)) {
            is ListAccountsResult.Success -> ctx.json(result.accounts)
            is ListAccountsResult.NotFound -> throw NotFoundResponse()
            is ListAccountsResult.Forbidden -> throw ForbiddenResponse()
        }
    }

    @Get("/{id}")
    fun info(ctx: Context, @Header("Authorization") authorization: String?, @Param id: UUID) {
        val tenant = ctx.queryParam("tenant")?.toUUIDOrNull()

        when (val result = getAccountHandler.execute(GetAccountCommand(authorization, tenant, id))) {
            is GetAccountResult.Success -> ctx.json(result.account)

            is GetAccountResult.NotFound -> throw NotFoundResponse()
            is GetAccountResult.Forbidden -> throw ForbiddenResponse()
        }
    }

    @Post
    fun create(ctx: Context, @Header("Authorization") authorization: String?) {
        val payload = runCatching { ctx.bodyAsClass(CreateAccountPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid: " + ctx.body())

        val email = payload.email?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing email")
        val firstName = payload.firstName?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing first_name")
        val lastName = payload.lastName?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing last_name")

        when (val result = createAccountHandler.execute(CreateAccountCommand(
            authorization = authorization,
            email = email,
            firstName = firstName,
            lastName = lastName,
        ))) {
            is CreateAccountResult.Success -> ctx.status(201).json(result.account)
            is CreateAccountResult.Forbidden -> throw ForbiddenResponse()
        }
    }
}
