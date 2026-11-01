package com.jasminesoftwaresolutions.idinterfaces.application.authorization

import com.jasminesoftwaresolutions.id.domain.events.*
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRole
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRoleAssignment
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IRoleAssignmentRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IRoleRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import com.jasminesoftwaresolutions.idinterfaces.services.auth.ScopedListHelper
import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.CompiledResourcePath
import io.javalin.community.routing.annotations.Delete
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.community.routing.annotations.Put
import io.javalin.http.*
import java.time.Instant
import java.util.*

class RestRoleController(
    private val roleRepository: IRoleRepository,
    private val roleAssignmentRepository: IRoleAssignmentRepository,
    private val tenantRepository: ITenantRepository,
    private val accountRepository: IAccountRepository,
    private val clientRepository: IClientRepository,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
    private val scopedListHelper: ScopedListHelper,
    private val eventDispatcher: IEventDispatcher,
) {
    // ----- Response types -----

    data class RoleResponse(
        val id: UUID,
        val name: String,
        val description: String?,
        /** The tenant this role is scoped to, or `null` for platform scope. */
        val tenantId: UUID?,
    )

    data class RoleAssignmentResponse(
        val id: UUID,
        val roleId: UUID,
        val roleName: String?,
        val accountId: UUID?,
        val accountEmail: String?,
        val clientId: UUID?,
        val clientName: String?,
        val expiresAt: Instant?,
    )

    // ----- Payload types -----

    data class CreateRolePayload(
        val name: String?,
        val description: String?,
        val tenant_id: UUID?,
    )

    data class UpdateRolePayload(
        val name: String?,
        val description: String?,
    )

    data class CreateRoleAssignmentPayload(
        val account_id: UUID?,
        val client_id: UUID?,
        val expires_at: Instant?,
    )

    // ----- Helpers -----

    private fun IRole.toResponse() = RoleResponse(
        id = id,
        name = name,
        description = description,
        tenantId = tenant?.id,
    )

    private fun IRoleAssignment.toResponse() = RoleAssignmentResponse(
        id = id,
        roleId = role.id,
        roleName = role.name,
        accountId = account?.id,
        accountEmail = account?.email,
        clientId = client?.id,
        clientName = client?.fqdn,
        expiresAt = expiresAt,
    )

    /** Require valid authentication (any type), throws on failure. */
    private fun Context.requireAuth(): PrincipalAuthorizationContext {
        return authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
    }

    /** Require session authentication, throws on failure. */
    private fun Context.requireSession(): PrincipalAuthorizationContext.SessionAuth {
        val auth = authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
        return auth as? PrincipalAuthorizationContext.SessionAuth ?: throw ForbiddenResponse()
    }

    /** Generic authorization check against a compiled resource path. */
    private fun Context.authorizeResource(resource: CompiledResourcePath, action: String) {
        val auth = requireAuth()
        val allowed = evaluationService.evaluate(auth.principalId, action, resource).get()
        if (!allowed) throw ForbiddenResponse("You are not permitted to $action on $resource")
    }

    /** Resolve the tenant a role is scoped to, or `null` for platform scope. */
    private fun resolveTenantScope(tenantId: UUID?): ITenant? =
        tenantId?.let { tenantRepository.findById(it) ?: throw BadRequestResponse("Unknown tenant_id") }

    // ----- Role CRUD (list) -----

    @Get("/api/v1/roles")
    fun listRoles(ctx: Context) {
        val auth = ctx.requireAuth()
        val all = roleRepository.findByTenant(null)
        val visible = scopedListHelper.filterVisible(
            principalId = auth.principalId,
            action = IdActions.LIST_ROLES,
            entities = all,
            resourceFn = { IdResources.role(it) },
        )
        ctx.json(visible.map { it.toResponse() })
    }

    @Get("/api/v1/entities/roles")
    fun listRolesForSelector(ctx: Context) {
        val auth = ctx.requireAuth()
        val all = roleRepository.entries()
        val visible = scopedListHelper.filterVisible(
            principalId = auth.principalId,
            action = IdActions.LIST_ROLES,
            entities = all,
            resourceFn = { IdResources.role(it.id) },
        )
        ctx.json(visible.map { mapOf("id" to it.id, "name" to it.name) })
    }

    @Get("/api/v1/tenants/{tenantId}/roles")
    fun listTenantRoles(ctx: Context, @Param tenantId: UUID) {
        val auth = ctx.requireAuth()
        val tenant = tenantRepository.findById(tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        val all = roleRepository.findByTenant(tenant)
        val visible = scopedListHelper.filterVisible(
            principalId = auth.principalId,
            action = IdActions.LIST_ROLES,
            entities = all,
            resourceFn = { IdResources.role(it) },
        )
        ctx.json(visible.map { it.toResponse() })
    }

    // ----- Role CRUD (single) -----

    @Post("/api/v1/roles")
    fun createRole(ctx: Context) {
        val payload = runCatching { ctx.bodyAsClass(CreateRolePayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val name = payload.name?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing name")

        val description = payload.description?.trim().takeUnless { it.isNullOrBlank() }

        val tenant = resolveTenantScope(payload.tenant_id)

        // Authorize based on scope: the tenant itself, or the platform root
        val resource = tenant?.let { IdResources.tenant(it.id) } ?: IdResources.platform()
        ctx.authorizeResource(resource, IdActions.CREATE_ROLE)

        val role = roleRepository.create {
            this.name = name
            this.description = description
            this.tenant = tenant
        }

        ctx.status(201)
        ctx.json(role.toResponse())

        eventDispatcher.dispatch(
            RoleCreatedEvent(
                principalId = ctx.requireAuth().principalId,
                action = IdActions.CREATE_ROLE,
                resource = IdResources.role(role).toString(),
                timestamp = Instant.now(),
                roleId = role.id,
                roleName = name,
                description = description,
                tenantId = tenant?.id,
            )
        )
    }

    @Put("/api/v1/roles/{roleId}")
    fun updateRole(ctx: Context, @Param roleId: UUID) {
        val role = roleRepository.findById(roleId)
            ?: throw NotFoundResponse("Role not found")

        ctx.authorizeResource(IdResources.role(role), IdActions.UPDATE_ROLE)

        val previousName = role.name
        val previousDescription = role.description

        val payload = runCatching { ctx.bodyAsClass(UpdateRolePayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val newName = payload.name?.trim()?.takeUnless { it.isEmpty() }
            ?: throw BadRequestResponse("Missing name")

        val newDescription = payload.description?.trim()?.takeUnless { it.isEmpty() }

        roleRepository.update(role) {
            this.name = newName
            this.description = newDescription
        }

        ctx.json(roleRepository.findById(roleId)!!.toResponse())

        eventDispatcher.dispatch(
            RoleUpdatedEvent(
                principalId = ctx.requireAuth().principalId,
                action = IdActions.UPDATE_ROLE,
                resource = IdResources.role(role).toString(),
                timestamp = Instant.now(),
                roleId = roleId,
                previousName = previousName,
                newName = newName,
                previousDescription = previousDescription,
                newDescription = newDescription,
            )
        )
    }

    @Delete("/api/v1/roles/{roleId}")
    fun deleteRole(ctx: Context, @Param roleId: UUID) {
        val role = roleRepository.findById(roleId)
            ?: throw NotFoundResponse("Role not found")

        ctx.authorizeResource(IdResources.role(role), IdActions.DELETE_ROLE)

        val roleNameSnapshot = role.name
        val roleIdSnapshot = role.id
        roleRepository.delete(role)

        ctx.status(200)

        eventDispatcher.dispatch(
            RoleDeletedEvent(
                principalId = ctx.requireAuth().principalId,
                action = IdActions.DELETE_ROLE,
                resource = IdResources.role(role).toString(),
                timestamp = Instant.now(),
                roleId = roleIdSnapshot,
                roleName = roleNameSnapshot,
            )
        )
    }

    @Get("/api/v1/admin/roles/{roleId}")
    fun getRole(ctx: Context, @Param roleId: UUID) {
        ctx.requireSession()

        val role = roleRepository.findById(roleId)
            ?: throw NotFoundResponse("Role not found")

        ctx.authorizeResource(IdResources.role(role), IdActions.GET_ROLE)

        ctx.json(role.toResponse())
    }

    // ----- Role assignments -----

    @Get("/api/v1/roles/{roleId}/assignments")
    fun listAssignments(ctx: Context, @Param roleId: UUID) {
        val role = roleRepository.findById(roleId)
            ?: throw NotFoundResponse("Role not found")

        ctx.authorizeResource(IdResources.role(role), IdActions.LIST_ROLE_ASSIGNMENTS)

        val assignments = roleAssignmentRepository.findByRole(role)
        ctx.json(assignments.map { it.toResponse() })
    }

    @Post("/api/v1/roles/{roleId}/assignments")
    fun createAssignment(ctx: Context, @Param roleId: UUID) {
        ctx.requireSession()

        val role = roleRepository.findById(roleId)
            ?: throw NotFoundResponse("Role not found")

        val payload = runCatching { ctx.bodyAsClass(CreateRoleAssignmentPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val accountId = payload.account_id
        val clientId = payload.client_id

        if ((accountId == null) == (clientId == null))
            throw BadRequestResponse("Exactly one of account_id or client_id must be provided")

        val account: IAccount? = accountId?.let {
            accountRepository.findById(it) ?: throw NotFoundResponse("Account not found")
        }
        val client: IClient? = clientId?.let {
            clientRepository.findById(it) ?: throw NotFoundResponse("Client not found")
        }

        ctx.authorizeResource(IdResources.role(role), IdActions.ASSIGN_ROLE)

        val assignment = roleAssignmentRepository.create {
            this.role = role
            this.account = account
            this.client = client
            this.expiresAt = payload.expires_at
        }

        ctx.status(201)
        ctx.json(assignment.toResponse())

        eventDispatcher.dispatch(
            RoleAssignedEvent(
                principalId = ctx.requireAuth().principalId,
                action = IdActions.ASSIGN_ROLE,
                resource = IdResources.role(role).toString(),
                timestamp = Instant.now(),
                assignmentId = assignment.id,
                roleId = role.id,
                roleName = role.name,
                accountId = account?.id,
                accountEmail = account?.email,
                clientId = client?.id,
                clientName = client?.fqdn,
                expiresAt = payload.expires_at,
            )
        )
    }

    @Delete("/api/v1/roles/{roleId}/assignments/{assignmentId}")
    fun deleteAssignment(ctx: Context, @Param roleId: UUID, @Param assignmentId: UUID) {
        ctx.requireSession()

        val role = roleRepository.findById(roleId)
            ?: throw NotFoundResponse("Role not found")

        val assignment = roleAssignmentRepository.findById(assignmentId)
            ?: throw NotFoundResponse("Assignment not found")

        if (assignment.role.id != role.id)
            throw BadRequestResponse("Assignment does not belong to the specified role")

        ctx.authorizeResource(IdResources.role(role), IdActions.UNASSIGN_ROLE)

        val account = assignment.account
        val client = assignment.client
        val assignmentIdSnapshot = assignment.id
        val accountIdSnapshot = account?.id
        val accountEmailSnapshot = account?.email
        val clientIdSnapshot = client?.id
        val clientNameSnapshot = client?.fqdn

        roleAssignmentRepository.delete(assignment)

        ctx.status(200)

        eventDispatcher.dispatch(
            RoleUnassignedEvent(
                principalId = ctx.requireAuth().principalId,
                action = IdActions.UNASSIGN_ROLE,
                resource = IdResources.role(role).toString(),
                timestamp = Instant.now(),
                assignmentId = assignmentIdSnapshot,
                roleId = role.id,
                roleName = role.name,
                accountId = accountIdSnapshot,
                accountEmail = accountEmailSnapshot,
                clientId = clientIdSnapshot,
                clientName = clientNameSnapshot,
            )
        )
    }

    // ----- Account/Client role listing -----

    @Get("/api/v1/accounts/{accountId}/roles")
    fun listAccountRoles(ctx: Context, @Param accountId: UUID) {
        val auth = ctx.requireAuth()

        val account = accountRepository.findById(accountId)
            ?: throw NotFoundResponse("Account not found")

        // Fast path: session owner always has access to their own account's roles
        if (auth !is PrincipalAuthorizationContext.SessionAuth || auth.session.account.id != accountId) {
            ctx.authorizeResource(IdResources.account(accountId), IdActions.LIST_ACCOUNT_ROLES)
        }

        val assigned = roleAssignmentRepository.findByAccount(account)

        ctx.json(assigned.map { it.toResponse() })
    }

    @Get("/api/v1/clients/{clientId}/roles")
    fun listClientRoles(ctx: Context, @Param clientId: UUID) {
        val auth = ctx.requireAuth()

        val client = clientRepository.findById(clientId)
            ?: throw NotFoundResponse("Client not found")

        // Clients can always see their own role assignments
        if (auth.principalId != clientId) {
            // For cross-client access, authorize against the client resource
            ctx.authorizeResource(IdResources.client(clientId), IdActions.LIST_CLIENT_ROLES)
        }

        val assigned = roleAssignmentRepository.findByClient(client)

        ctx.json(assigned.map { it.toResponse() })
    }
}
