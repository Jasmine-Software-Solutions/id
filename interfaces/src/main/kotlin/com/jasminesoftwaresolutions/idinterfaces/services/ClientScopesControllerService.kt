package com.jasminesoftwaresolutions.idinterfaces.services

import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.registries.IScopeRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IScopeRepository
import java.util.*

open class ClientScopesControllerService<T : IRegisteredScope>(
    protected val clientRepository: IClientRepository<out IClient>,
    protected val scopeRepository: IScopeRepository<T>,
    protected val scopeRegistry: IScopeRegistry<out IScope>,
) {
    data class ScopeRegistration(val id: String, val description: String, val isByTenant: Boolean)
    data class RegisteredScopeDTO(val id: String, val description: String, val isByTenant: Boolean)

    open class ReplaceResult {
        object Forbidden : ReplaceResult()
        object ClientNotFound : ReplaceResult()
        class InvalidScopeId(val id: String) : ReplaceResult()
        class ReservedScopeId(val id: String) : ReplaceResult()
        class ScopeOwnedByAnotherClient(val id: String) : ReplaceResult()
        data class Success(val client: IClient, val scopes: List<RegisteredScopeDTO>) : ReplaceResult()
    }

    private val scopeIdPattern = Regex("^[\\u0021\\u0023-\\u005B\\u005D-\\u007E]+$")

    protected fun canManageClientScopes(authentication: IAuthorizationContext, clientId: UUID): Boolean {
        if (!authentication.privileges.contains(ClientScopesWritePrivilege))
            return false

        // Client-authenticated calls are limited to their own registrations.
        if (authentication is IClientAuthorizationContext && authentication.client.id != clientId)
            return false

        return true
    }

    open fun replace(
        authentication: IAuthorizationContext,
        clientId: UUID,
        scopes: List<ScopeRegistration>
    ): ReplaceResult {
        if (!canManageClientScopes(authentication, clientId))
            return ReplaceResult.Forbidden

        val client = clientRepository.findById(clientId)
            ?: return ReplaceResult.ClientNotFound

        val desiredById = linkedMapOf<String, ScopeRegistration>()
        for (scope in scopes) {
            if (!scopeIdPattern.matches(scope.id))
                return ReplaceResult.InvalidScopeId(scope.id)

            // Static scope IDs remain code-defined and cannot be redefined by clients.
            if (scopeRegistry.findById(scope.id) != null)
                return ReplaceResult.ReservedScopeId(scope.id)

            desiredById[scope.id] = scope
        }

        val existing = scopeRepository.findByClient(clientId)
        val existingById = existing.associateBy { it.id }

        for (scope in existing) {
            if (!desiredById.containsKey(scope.id))
                scopeRepository.delete(scope)
        }

        for ((scopeId, scopeInput) in desiredById) {
            val current = existingById[scopeId] ?: scopeRepository.findById(scopeId)

            if (current == null) {
                scopeRepository.create {
                    this.client = client
                    this.id = scopeId
                    this.description = scopeInput.description
                    this.isByTenant = scopeInput.isByTenant
                }

                continue
            }

            if (current.client?.id != clientId)
                return ReplaceResult.ScopeOwnedByAnotherClient(scopeId)

            scopeRepository.update(current) {
                this.client = client
                this.description = scopeInput.description
                this.isByTenant = scopeInput.isByTenant
            }
        }

        val updated = scopeRepository.findByClient(clientId)
            .sortedBy { it.id }
            .map { RegisteredScopeDTO(it.id, it.description, it.isByTenant) }

        return ReplaceResult.Success(client, updated)
    }
}
