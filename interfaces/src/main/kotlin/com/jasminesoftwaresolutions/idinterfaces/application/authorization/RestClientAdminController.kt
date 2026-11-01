package com.jasminesoftwaresolutions.idinterfaces.application.authorization

import com.jasminesoftwaresolutions.id.domain.events.*
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.services.IHashFunction
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import io.javalin.community.routing.annotations.Delete
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.community.routing.annotations.Put
import io.javalin.http.*
import java.util.*

/**
 * Admin CRUD for OAuth2 clients (create, read, update, delete).
 * Scope management is handled separately via [RestClientScopesController].
 */
class RestClientAdminController(
    private val clientRepository: IClientRepository,
    private val hashFunction: IHashFunction,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
    private val eventDispatcher: IEventDispatcher,
) {
    data class ClientResponse(
        val id: UUID,
        val fqdn: String,
        val confidential: Boolean,
        val suspended: Boolean,
        val redirectUris: List<String>,
        val createdAt: Long,
        val name: String?,
        val logoUri: String?,
        val initiateLoginUri: String?,
    )

    data class CreateClientPayload(
        val fqdn: String?,
        val confidential: Boolean?,
        val redirect_uris: List<String>?,
        val name: String?,
        val logo_uri: String?,
        val initiate_login_uri: String?,
    )

    data class UpdateClientPayload(
        val confidential: Boolean?,
        val suspended: Boolean?,
        val redirect_uris: List<String>?,
        val name: String?,
        val logo_uri: String?,
        val initiate_login_uri: String?,
    )

    data class ResetSecretResponse(
        val secret: String,
    )

    private val fqdnPattern =
        Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*$")

    private fun IClient.toResponse() = ClientResponse(
        id = id,
        fqdn = fqdn,
        confidential = confidential,
        suspended = suspended,
        redirectUris = redirectUris.values.map { it.toString() },
        createdAt = createdAt.epochSecond,
        name = name,
        logoUri = logoUri?.toString(),
        initiateLoginUri = initiateLoginUri?.toString(),
    )

    /** Require valid session, throws on failure. */
    private fun Context.requireSession(): PrincipalAuthorizationContext.SessionAuth {
        val auth = authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
        return auth as? PrincipalAuthorizationContext.SessionAuth ?: throw ForbiddenResponse()
    }

    /** Authorize an admin action on the platform resource. */
    private fun Context.authorizePlatform(action: String) {
        val auth = requireSession()
        val allowed = evaluationService.evaluate(auth.principalId, action, IdResources.platform()).get()
        if (!allowed) throw ForbiddenResponse("Insufficient privileges: $action")
    }

    /** Authorize an admin action on a specific client resource. */
    private fun Context.authorizeClient(clientId: UUID, action: String) {
        val auth = requireSession()
        val allowed = evaluationService.evaluate(auth.principalId, action, IdResources.client(clientId)).get()
        if (!allowed) throw ForbiddenResponse("Insufficient privileges: $action on client $clientId")
    }

    // ----- Client listing -----

    @Get("/api/v1/admin/clients")
    fun listClients(ctx: Context) {
        ctx.authorizePlatform(IdActions.LIST_CLIENTS)
        val clients = clientRepository.entries()
        ctx.json(clients.map { it.toResponse() })
    }

    @Get("/api/v1/admin/clients/{clientId}")
    fun getClient(ctx: Context, @Param clientId: UUID) {
        ctx.authorizeClient(clientId, IdActions.GET_CLIENT)

        val client = clientRepository.findById(clientId)
            ?: throw NotFoundResponse("Client not found")

        ctx.json(client.toResponse())
    }

    // ----- Client CRUD -----

    @Post("/api/v1/admin/clients")
    fun createClient(ctx: Context) {
        ctx.authorizePlatform(IdActions.CREATE_CLIENT)

        val payload = runCatching { ctx.bodyAsClass(CreateClientPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val fqdn = payload.fqdn?.trim()?.takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing fqdn")

        if (!fqdnPattern.matches(fqdn))
            throw BadRequestResponse("Invalid fqdn: must be a fully qualified domain name")

        // Generate a secure random secret
        val secretBytes = ByteArray(48)
        java.security.SecureRandom().nextBytes(secretBytes)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)

        val redirectUris = payload.redirect_uris?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val client = clientRepository.create {
            this.fqdn = fqdn
            this.confidential = payload.confidential ?: true
            this.secret = secret
            this.name = payload.name?.trim()?.takeUnless { it.isBlank() }
            this.logoUri = payload.logo_uri?.trim()?.takeUnless { it.isBlank() }?.let { java.net.URI(it) }
            this.initiateLoginUri =
                payload.initiate_login_uri?.trim()?.takeUnless { it.isBlank() }?.let { java.net.URI(it) }
            for (uri in redirectUris) {
                this.redirectUris.add(java.net.URI(uri))
            }
        }

        ctx.status(201)
        ctx.json(client.toResponse())

        val session = ctx.requireSession()
        eventDispatcher.dispatch(
            ClientCreatedEvent(
                principalId = session.principalId,
                action = IdActions.CREATE_CLIENT,
                resource = IdResources.platform().toString(),
                timestamp = java.time.Instant.now(),
                clientId = client.id,
                clientName = client.fqdn,
                confidential = client.confidential,
                logoUri = client.logoUri?.toString(),
                initiateLoginUri = client.initiateLoginUri?.toString(),
            )
        )
    }

    @Put("/api/v1/admin/clients/{clientId}")
    fun updateClient(ctx: Context, @Param clientId: UUID) {
        ctx.authorizeClient(clientId, IdActions.UPDATE_CLIENT)

        val client = clientRepository.findById(clientId)
            ?: throw NotFoundResponse("Client not found")

        val previousConfidential = client.confidential
        val previousSuspended = client.suspended
        val previousName = client.name
        val previousLogoUri = client.logoUri?.toString()
        val previousInitiateLoginUri = client.initiateLoginUri?.toString()

        val payload = runCatching { ctx.bodyAsClass(UpdateClientPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        clientRepository.update(client) {
            payload.confidential?.let { this.confidential = it }
            payload.suspended?.let { this.suspended = it }
            payload.name?.let { this.name = it.trim().takeUnless { it.isBlank() } }
            payload.logo_uri?.let { this.logoUri = it.trim().takeUnless { it.isBlank() }?.let { java.net.URI(it) } }
            payload.initiate_login_uri?.let {
                this.initiateLoginUri = it.trim().takeUnless { it.isBlank() }?.let { java.net.URI(it) }
            }
            payload.redirect_uris?.let { uris ->
                // Replace all redirect URIs
                val current = this.redirectUris.values.toList()
                for (uri in current) {
                    this.redirectUris.remove(uri)
                }
                for (raw in uris.map { it.trim() }.filter { it.isNotEmpty() }) {
                    this.redirectUris.add(java.net.URI(raw))
                }
            }
        }

        val updated = clientRepository.findById(clientId)!!
        ctx.json(updated.toResponse())

        val session = ctx.requireSession()
        val confidentialChanged = payload.confidential
        val suspendedChanged = payload.suspended
        val nameChanged = payload.name
        val logoUriChanged = payload.logo_uri
        val initiateLoginUriChanged = payload.initiate_login_uri
        eventDispatcher.dispatch(
            ClientUpdatedEvent(
                principalId = session.principalId,
                action = IdActions.UPDATE_CLIENT,
                resource = IdResources.client(clientId).toString(),
                timestamp = java.time.Instant.now(),
                clientId = clientId,
                previousName = if (nameChanged != null) previousName else null,
                newName = if (nameChanged != null) updated.name else null,
                previousConfidential = if (confidentialChanged != null) previousConfidential else null,
                newConfidential = if (confidentialChanged != null) updated.confidential else null,
                previousSuspended = if (suspendedChanged != null) previousSuspended else null,
                newSuspended = if (suspendedChanged != null) updated.suspended else null,
                previousLogoUri = if (logoUriChanged != null) previousLogoUri else null,
                newLogoUri = if (logoUriChanged != null) updated.logoUri?.toString() else null,
                previousInitiateLoginUri = if (initiateLoginUriChanged != null) previousInitiateLoginUri else null,
                newInitiateLoginUri = if (initiateLoginUriChanged != null) updated.initiateLoginUri?.toString() else null,
            )
        )
    }

    @Delete("/api/v1/admin/clients/{clientId}")
    fun deleteClient(ctx: Context, @Param clientId: UUID) {
        ctx.authorizeClient(clientId, IdActions.DELETE_CLIENT)

        val client = clientRepository.findById(clientId)
            ?: throw NotFoundResponse("Client not found")

        clientRepository.delete(client)
        ctx.status(200)

        val session = ctx.requireSession()
        eventDispatcher.dispatch(
            ClientDeletedEvent(
                principalId = session.principalId,
                action = IdActions.DELETE_CLIENT,
                resource = IdResources.client(clientId).toString(),
                timestamp = java.time.Instant.now(),
                clientId = clientId,
                clientName = client.fqdn,
            )
        )
    }

    // ----- Secret management -----

    @Post("/api/v1/admin/clients/{clientId}/reset-secret")
    fun resetSecret(ctx: Context, @Param clientId: UUID) {
        ctx.authorizeClient(clientId, IdActions.RESET_CLIENT_SECRET)

        val client = clientRepository.findById(clientId)
            ?: throw NotFoundResponse("Client not found")

        val secretBytes = ByteArray(48)
        java.security.SecureRandom().nextBytes(secretBytes)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)

        clientRepository.update(client) {
            this.secret = secret
        }

        ctx.json(ResetSecretResponse(secret))

        val session = ctx.requireSession()
        eventDispatcher.dispatch(
            ClientSecretResetEvent(
                principalId = session.principalId,
                action = IdActions.RESET_CLIENT_SECRET,
                resource = IdResources.client(clientId).toString(),
                timestamp = java.time.Instant.now(),
                clientId = clientId,
                clientName = client.fqdn,
            )
        )
    }
}
