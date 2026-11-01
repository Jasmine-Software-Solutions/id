package com.jasminesoftwaresolutions.idinterfaces.application.authorization

import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IScopeRepository
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import io.javalin.community.routing.annotations.Put
import io.javalin.http.*
import java.util.*

class RestClientScopesController(
    private val clientRepository: IClientRepository,
    private val scopeRepository: IScopeRepository,
    private val scopeRegistry: IScopeRegistry<IScope>,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
) {
    data class ScopePayload(
        val id: String?,
        val description: String?,
    )

    data class ReplaceRequest(
        val scopes: List<ScopePayload>?,
    )

    private val scopeIdPattern = Regex("^[\\u0021\\u0023-\\u005B\\u005D-\\u007E]+$")

    @Put("/api/v1/clients/{client_id}/scopes")
    fun put(ctx: Context) {
        val auth = authorizationService.authenticate(ctx) ?: throw UnauthorizedResponse()

        val clientId = runCatching { UUID.fromString(ctx.pathParam("client_id")) }.getOrNull()
            ?: throw BadRequestResponse("Invalid client_id")

        // Client-authenticated calls are limited to their own registrations;
        // session-authenticated callers are evaluated through Witchwood.
        if (auth is PrincipalAuthorizationContext.SessionAuth) {
            val allowed = evaluationService.evaluate(auth.principalId, IdActions.MANAGE_CLIENT_SCOPES, IdResources.client(clientId)).get()
            if (!allowed) throw ForbiddenResponse("Insufficient privileges: ${IdActions.MANAGE_CLIENT_SCOPES} on client $clientId")
        } else if (auth.principalId != clientId) {
            throw ForbiddenResponse()
        }

        val request = runCatching { ctx.bodyAsClass(ReplaceRequest::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val desiredScopes = request.scopes?.map { payload ->
            val id = payload.id
                ?: throw BadRequestResponse("Missing scope id")
            val description = payload.description
                ?: throw BadRequestResponse("Missing scope description")

            if (!scopeIdPattern.matches(id))
                throw BadRequestResponse("Invalid scope id: $id")

            // Static scope IDs remain code-defined and cannot be redefined by clients
            if (scopeRegistry.findById(id) != null)
                throw BadRequestResponse("Reserved scope id: $id")

            id to description
        } ?: throw BadRequestResponse("Missing scopes")

        val client = clientRepository.findById(clientId)
            ?: throw NotFoundResponse("Client not found")

        val desiredById = desiredScopes.toMap()

        val existing = scopeRepository.findByClient(clientId)
        val existingById = existing.associateBy { it.id }

        // Delete removed scopes
        for (scope in existing) {
            if (scope.id !in desiredById)
                scopeRepository.delete(scope)
        }

        // Create or update scopes
        for ((scopeId, description) in desiredById) {
            val current = existingById[scopeId] ?: scopeRepository.findById(scopeId)

            if (current == null) {
                scopeRepository.create {
                    this.client = client
                    this.id = scopeId
                    this.description = description
                }
                continue
            }

            if (current.client?.id != clientId)
                throw BadRequestResponse("Scope id already owned by another client: $scopeId")

            scopeRepository.update(current) {
                this.client = client
                this.description = description
            }
        }

        val updated = scopeRepository.findByClient(clientId).sortedBy { it.id }

        ctx.status(200)
        ctx.json(
            mapOf(
                "client_id" to client.id,
                "scopes" to updated.map { mapOf("id" to it.id, "description" to it.description) }
            )
        )
    }
}
