package com.jasminesoftwaresolutions.idinterfaces.application.authorization

import com.fasterxml.jackson.annotation.JsonProperty
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRegisteredScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinAuthorizationService
import com.jasminesoftwaresolutions.idinterfaces.services.client.ClientScopesControllerService
import io.javalin.community.routing.annotations.Put
import io.javalin.http.*
import java.util.*

class RestClientScopesController<T : IRegisteredScope>(
    private val service: ClientScopesControllerService<T>,
    private val authorizationService: JavalinAuthorizationService
) {
    data class ScopePayload(
        @get:JsonProperty("id") val id: String?,
        @get:JsonProperty("description") val description: String?,
        @get:JsonProperty("is_by_tenant") val isByTenant: Boolean?
    )

    data class ReplaceRequest(
        @get:JsonProperty("scopes") val scopes: List<ScopePayload>?
    )

    data class UnregisteredScope(
        override val id: String,
        override val description: String,
        override val isByTenant: Boolean
    ) : IScope {
        override var client: IClient?
            get() = throw UnsupportedOperationException()
            set(_) = throw UnsupportedOperationException()
    }

    @Put("/api/v1/clients/{client_id}/scopes")
    fun put(ctx: Context) {
        val authentication = authorizationService.authenticate(ctx)
            ?: throw UnauthorizedResponse()

        val clientId = runCatching { UUID.fromString(ctx.pathParam("client_id")) }.getOrNull()
            ?: throw BadRequestResponse("Invalid client_id")

        val request = runCatching { ctx.bodyAsClass(ReplaceRequest::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val scopes = request.scopes
            ?.map { scope ->
                val id = scope.id
                    ?: throw BadRequestResponse("Missing scope id")
                val description = scope.description
                    ?: throw BadRequestResponse("Missing scope description")
                val isByTenant = scope.isByTenant ?: false

                UnregisteredScope(id, description, isByTenant)
            } ?: throw BadRequestResponse("Missing scopes")

        when (val result = service.replace(authentication, clientId, scopes)) {
            is ClientScopesControllerService.ReplaceResult.Success -> {
                ctx.json(
                    mapOf(
                        "client_id" to result.client.id,
                        "scopes" to result.scopes
                    )
                )
            }

            is ClientScopesControllerService.ReplaceResult.ClientNotFound -> throw NotFoundResponse("Client not found")
            is ClientScopesControllerService.ReplaceResult.Forbidden -> throw ForbiddenResponse()
            is ClientScopesControllerService.ReplaceResult.InvalidScopeId -> throw BadRequestResponse("Invalid scope id: ${result.id}")
            is ClientScopesControllerService.ReplaceResult.ReservedScopeId -> throw BadRequestResponse("Reserved scope id: ${result.id}")
            is ClientScopesControllerService.ReplaceResult.ScopeOwnedByAnotherClient -> throw BadRequestResponse("Scope id already owned by another client: ${result.id}")
        }
    }
}
