package com.jasminesoftwaresolutions.id.domain.services.authorization

import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.CompiledResourcePath
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Service for evaluating authorization decisions against Witchwood.
 * Witchwood resolves policies, roles, and conditions to produce a boolean
 * allow/deny result for a given principal, action, and resource.
 */
interface IAuthorizationService {
    /**
     * Evaluate whether [principalId] is allowed to perform [action] on [resource].
     *
     * @param principalId the UUID of the principal (account or client)
     * @param action the action being performed (e.g., "read", "write", "delete")
     * @param resource the compiled resource path being accessed
     * @param context optional key-value context for condition evaluation (e.g., IP address, time of day)
     * @return CompletableFuture resolving to true if allowed, false if denied
     */
    fun evaluate(
        principalId: UUID,
        action: String,
        resource: CompiledResourcePath,
        context: Map<String, String> = emptyMap()
    ): CompletableFuture<Boolean>

    /**
     * Query Witchwood for all resources on which [principalId] can perform [action].
     *
     * Unlike [evaluate] which tests a single resource, this returns the full set of
     * concrete resource paths for which the principal has an ALLOW decision.
     *
     * This enables scoped listing: rather than returning all entities and relying on
     * 403s per-entity, the caller queries the set of accessible resources first and
     * filters the response accordingly.
     */
    fun queryResources(
        principalId: UUID,
        action: String,
    ): CompletableFuture<Set<CompiledResourcePath>>
}
