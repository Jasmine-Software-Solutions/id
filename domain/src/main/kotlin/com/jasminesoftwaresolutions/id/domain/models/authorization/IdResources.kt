package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.CompiledResourcePath
import java.util.*

/**
 * Centralized resource path builders for Witchwood policy evaluation.
 *
 * Resource paths reflect the hierarchy: scoped entities include their full
 * containment path so that policies can target specific levels.
 *
 * Examples:
 * - Platform role:       `id.jasmine.software://role:{roleId}`
 * - Tenant-scoped role:  `id.jasmine.software://tenant:{t}/role:{roleId}`
 * - Tenant-scoped policy: `id.jasmine.software://tenant:{t}/policy:{policyId}`
 */
object IdResources {
    private const val SERVICE = "id.jasmine.software"

    /** Platform root resource: `id.jasmine.software://` */
    fun platform(): CompiledResourcePath =
        CompiledResourcePath.builder()
            .service(SERVICE)
            .build()

    /** `id.jasmine.software://account:{accountId}` */
    fun account(accountId: UUID): CompiledResourcePath =
        CompiledResourcePath.builder()
            .service(SERVICE)
            .one("account", accountId)
            .build()

    /** `id.jasmine.software://account:{accountId}/session:{sessionId}` */
    fun session(accountId: UUID, sessionId: UUID): CompiledResourcePath =
        CompiledResourcePath.builder()
            .service(SERVICE)
            .one("account", accountId)
            .one("session", sessionId)
            .build()

    /** `id.jasmine.software://client:{clientId}` */
    fun client(clientId: UUID): CompiledResourcePath =
        CompiledResourcePath.builder()
            .service(SERVICE)
            .one("client", clientId)
            .build()

    /** `id.jasmine.software://role:{roleId}` (platform-scoped) */
    fun role(roleId: UUID): CompiledResourcePath =
        CompiledResourcePath.builder()
            .service(SERVICE)
            .one("role", roleId)
            .build()

    /**
     * Resource path for a role, e.g.:
     * `id.jasmine.software://tenant:{t}/role:{roleId}`, or `id.jasmine.software://role:{roleId}`
     * when the role is platform-scoped.
     */
    fun role(role: IRole): CompiledResourcePath {
        val b = CompiledResourcePath.builder().service(SERVICE)
        role.tenant?.let { b.one("tenant", it.id) }
        b.one("role", role.id)
        return b.build()
    }

    /** `id.jasmine.software://policy:{policyId}` (platform-scoped) */
    fun policy(policyId: UUID): CompiledResourcePath =
        CompiledResourcePath.builder()
            .service(SERVICE)
            .one("policy", policyId)
            .build()

    /**
     * Resource path for a policy, e.g.:
     * `id.jasmine.software://tenant:{t}/policy:{policyId}`, or `id.jasmine.software://policy:{policyId}`
     * when the policy is platform-scoped.
     */
    fun policy(policy: IPolicy): CompiledResourcePath {
        val b = CompiledResourcePath.builder().service(SERVICE)
        policy.tenant?.let { b.one("tenant", it.id) }
        b.one("policy", policy.id)
        return b.build()
    }

    /** `id.jasmine.software://tenant:{tenantId}` */
    fun tenant(tenantId: UUID): CompiledResourcePath =
        CompiledResourcePath.builder()
            .service(SERVICE)
            .one("tenant", tenantId)
            .build()

    /** `id.jasmine.software://tenant:{t}/membership:{m}` — used for admin actions scoped to a specific membership. */
    fun membership(tenantId: UUID, membershipId: UUID): CompiledResourcePath {
        val b = CompiledResourcePath.builder().service(SERVICE)
        b.one("tenant", tenantId)
        b.one("membership", membershipId)
        return b.build()
    }
}
