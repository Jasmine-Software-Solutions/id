package com.jasminesoftwaresolutions.idinterfaces.application.tenant

import com.jasminesoftwaresolutions.id.domain.events.*
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import com.jasminesoftwaresolutions.idinterfaces.services.auth.ScopedListHelper
import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.CompiledResourcePath
import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.ResourcePathMatcher
import io.javalin.community.routing.annotations.Delete
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.community.routing.annotations.Put
import io.javalin.http.*
import java.util.*
import java.time.Instant

/**
 * CRUD for tenants.
 */
class RestTenantController(
    private val tenantRepository: ITenantRepository,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
    private val scopedListHelper: ScopedListHelper,
    private val eventDispatcher: IEventDispatcher,
) {
    data class TenantResponse(val id: UUID, val name: String, val suspended: Boolean)

    data class CreateTenantPayload(val name: String?, val suspended: Boolean?)
    data class UpdateTenantPayload(val name: String?, val suspended: Boolean?)

    private fun Context.requireSession(): PrincipalAuthorizationContext.SessionAuth {
        val auth = authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
        return auth as? PrincipalAuthorizationContext.SessionAuth ?: throw ForbiddenResponse()
    }

    /** Like requireSession but also accepts Bearer tokens (for service-to-service calls). */
    private fun Context.requireAuth(): PrincipalAuthorizationContext {
        return authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
    }

    private fun Context.authorizeResource(resource: CompiledResourcePath, action: String) {
        val auth = requireSession()
        val allowed = evaluationService.evaluate(auth.principalId, action, resource).get()
        if (!allowed) throw ForbiddenResponse("Insufficient privileges: $action")
    }

    @Get("/api/v1/tenants")
    fun listTenants(ctx: Context) {
        val auth = ctx.requireAuth()
        val all = tenantRepository.entries()

        // If principal can ListTenants on the platform root, return everything
        val platformAllowed = try {
            evaluationService.evaluate(auth.principalId, IdActions.LIST_TENANTS, IdResources.platform()).get()
        } catch (_: Exception) { false }

        if (platformAllowed) {
            ctx.json(all.map { TenantResponse(it.id, it.name, it.suspended) })
            return
        }

        // Otherwise, query which tenants the principal can GetTenant on
        val patterns = try {
            evaluationService.queryResources(auth.principalId, IdActions.GET_TENANT).get()
        } catch (_: Exception) { emptySet() }

        val visible = all.filter { tenant ->
            val resource = IdResources.tenant(tenant.id)
            patterns.any { pattern -> ResourcePathMatcher.matches(pattern, resource) }
        }
        ctx.json(visible.map { TenantResponse(it.id, it.name, it.suspended) })
    }

    @Post("/api/v1/tenants")
    fun createTenant(ctx: Context) {
        ctx.authorizeResource(IdResources.platform(), IdActions.CREATE_TENANT)
        val p = ctx.bodyAsClass(CreateTenantPayload::class.java)
        val name = p.name?.trim()?.takeUnless { it.isNullOrBlank() } ?: throw BadRequestResponse("Missing name")
        val tenant = tenantRepository.create {
            this.name = name
            this.suspended = p.suspended ?: false
        }
        ctx.status(201).json(TenantResponse(tenant.id, tenant.name, tenant.suspended))
        eventDispatcher.dispatch(
            TenantCreatedEvent(
                principalId = ctx.requireSession().principalId,
                action = IdActions.CREATE_TENANT,
                resource = IdResources.platform().toString(),
                timestamp = Instant.now(),
                tenantId = tenant.id,
                name = tenant.name,
                suspended = tenant.suspended,
            )
        )
    }

    @Put("/api/v1/tenants/{tenantId}")
    fun updateTenant(ctx: Context, @Param tenantId: UUID) {
        ctx.authorizeResource(IdResources.tenant(tenantId), IdActions.UPDATE_TENANT)
        val tenant = tenantRepository.findById(tenantId) ?: throw NotFoundResponse()
        val p = ctx.bodyAsClass(UpdateTenantPayload::class.java)
        val previousName = tenant.name
        val previousSuspended = tenant.suspended
        tenantRepository.update(tenant) {
            p.name?.trim()?.takeUnless { it.isEmpty() }?.let { this.name = it }
            p.suspended?.let { this.suspended = it }
        }
        val updated = tenantRepository.findById(tenantId)!!
        ctx.json(TenantResponse(updated.id, updated.name, updated.suspended))
        eventDispatcher.dispatch(
            TenantUpdatedEvent(
                principalId = ctx.requireSession().principalId,
                action = IdActions.UPDATE_TENANT,
                resource = IdResources.tenant(tenantId).toString(),
                timestamp = Instant.now(),
                tenantId = tenantId,
                previousName = if (previousName != updated.name) previousName else null,
                newName = if (previousName != updated.name) updated.name else null,
                previousSuspended = if (previousSuspended != updated.suspended) previousSuspended else null,
                newSuspended = if (previousSuspended != updated.suspended) updated.suspended else null,
            )
        )
    }

    @Delete("/api/v1/tenants/{tenantId}")
    fun deleteTenant(ctx: Context, @Param tenantId: UUID) {
        ctx.authorizeResource(IdResources.tenant(tenantId), IdActions.DELETE_TENANT)
        val tenant = tenantRepository.findById(tenantId) ?: throw NotFoundResponse()
        val tenantName = tenant.name
        tenantRepository.delete(tenant)
        ctx.status(200)
        eventDispatcher.dispatch(
            TenantDeletedEvent(
                principalId = ctx.requireSession().principalId,
                action = IdActions.DELETE_TENANT,
                resource = IdResources.tenant(tenantId).toString(),
                timestamp = Instant.now(),
                tenantId = tenantId,
                name = tenantName,
            )
        )
    }
}
