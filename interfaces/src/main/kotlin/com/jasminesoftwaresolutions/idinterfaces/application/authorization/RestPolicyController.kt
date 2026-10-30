package com.jasminesoftwaresolutions.idinterfaces.application.authorization

import com.google.gson.JsonArray
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authorization.IPolicy
import com.jasminesoftwaresolutions.id.domain.models.authorization.IPolicyAssignment
import com.jasminesoftwaresolutions.id.domain.models.authorization.IRole
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IClientRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IPolicyAssignmentRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IPolicyRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IRoleRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import com.jasminesoftwaresolutions.id.domain.events.*
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.services.authorization.PolicySandboxer
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

class RestPolicyController(
    private val policyRepository: IPolicyRepository,
    private val policyAssignmentRepository: IPolicyAssignmentRepository,
    private val tenantRepository: ITenantRepository,
    private val accountRepository: IAccountRepository,
    private val clientRepository: IClientRepository,
    private val roleRepository: IRoleRepository,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
    private val scopedListHelper: ScopedListHelper,
    private val eventDispatcher: IEventDispatcher,
) {
    // ----- Response types -----

    data class PolicyResponse(
        val id: UUID,
        val name: String,
        val description: String?,
        /** The tenant this policy is scoped to, or `null` for platform scope. */
        val tenantId: UUID?,
        val document: JsonArray,
    )

    data class PolicyAssignmentResponse(
        val id: UUID,
        val policyId: UUID,
        val policyName: String?,
        val accountId: UUID?,
        val accountEmail: String?,
        val clientId: UUID?,
        val clientName: String?,
        val roleId: UUID?,
        val roleName: String?,
        val expiresAt: Instant?,
    )

    // ----- Payload types -----

    data class CreatePolicyPayload(
        val name: String?,
        val description: String?,
        val tenant_id: UUID?,
        val document: JsonArray?,
    )

    data class UpdatePolicyPayload(
        val name: String?,
        val description: String?,
        val document: JsonArray?,
    )

    data class CreatePolicyAssignmentPayload(
        val account_id: UUID?,
        val client_id: UUID?,
        val role_id: UUID?,
        val expires_at: Instant?,
    )

    // ----- Helpers -----

    private fun IPolicy.toResponse() = PolicyResponse(
        id = id,
        name = name,
        description = description,
        tenantId = tenant?.id,
        document = document,
    )

    private fun IPolicyAssignment.toResponse() = PolicyAssignmentResponse(
        id = id,
        policyId = policy.id,
        policyName = policy.name,
        accountId = account?.id,
        accountEmail = account?.email,
        clientId = client?.id,
        clientName = client?.fqdn,
        roleId = role?.id,
        roleName = role?.name,
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
        if (!allowed) throw ForbiddenResponse("Insufficient privileges: $action")
    }

    /** Resolve the tenant a policy is scoped to, or `null` for platform scope. */
    private fun resolveTenantScope(tenantId: UUID?): ITenant? =
        tenantId?.let { tenantRepository.findById(it) ?: throw BadRequestResponse("Unknown tenant_id") }

    // ----- Policy listing -----

    @Get("/api/v1/policies")
    fun listPolicies(ctx: Context) {
        val auth = ctx.requireAuth()
        // Platform-scoped only, mirroring listRoles — tenant-scoped policies come from
        // GET /api/v1/tenants/{tenantId}/policies.
        val all = policyRepository.findByTenant(null)
        val visible = scopedListHelper.filterVisible(
            principalId = auth.principalId,
            action = IdActions.LIST_POLICIES,
            entities = all,
            resourceFn = { IdResources.policy(it) },
        )
        ctx.json(visible.map { it.toResponse() })
    }

    @Get("/api/v1/tenants/{tenantId}/policies")
    fun listTenantPolicies(ctx: Context, @Param tenantId: UUID) {
        val auth = ctx.requireAuth()
        val tenant = tenantRepository.findById(tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        val all = policyRepository.findByTenant(tenant)
        val visible = scopedListHelper.filterVisible(
            principalId = auth.principalId,
            action = IdActions.LIST_POLICIES,
            entities = all,
            resourceFn = { IdResources.policy(it) },
        )
        ctx.json(visible.map { it.toResponse() })
    }

    // ----- Policy CRUD (single) -----

    @Get("/api/v1/policies/{policyId}")
    fun getPolicy(ctx: Context, @Param policyId: UUID) {
        ctx.requireAuth()

        val policy = policyRepository.findById(policyId)
            ?: throw NotFoundResponse("Policy not found")

        ctx.authorizeResource(IdResources.policy(policy), IdActions.GET_POLICY)

        ctx.json(policy.toResponse())
    }

    @Post("/api/v1/policies")
    fun createPolicy(ctx: Context) {
        val session = ctx.requireSession()

        val payload = runCatching { ctx.bodyAsClass(CreatePolicyPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val name = payload.name?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing name")

        val tenant = resolveTenantScope(payload.tenant_id)

        val resource = tenant?.let { IdResources.tenant(it.id) } ?: IdResources.platform()
        ctx.authorizeResource(resource, IdActions.CREATE_POLICY)

        val document = payload.document
            ?: throw BadRequestResponse("Missing document")

        val scopedDocument = PolicySandboxer.wrapResourcesTo(document, tenant)

        val policy = policyRepository.create {
            this.name = name
            this.description = payload.description?.trim()?.takeUnless { it.isEmpty() }
            this.tenant = tenant
            this.document = scopedDocument
        }

        eventDispatcher.dispatch(
            PolicyCreatedEvent(
                principalId = session.principalId,
                action = IdActions.CREATE_POLICY,
                resource = IdResources.policy(policy).toString(),
                timestamp = Instant.now(),
                policyId = policy.id,
                policyName = policy.name,
                description = policy.description,
                tenantId = policy.tenant?.id,
                document = policy.document,
            )
        )

        ctx.status(201)
        ctx.json(policy.toResponse())
    }

    @Put("/api/v1/policies/{policyId}")
    fun updatePolicy(ctx: Context, @Param policyId: UUID) {
        val policy = policyRepository.findById(policyId)
            ?: throw NotFoundResponse("Policy not found")

        val auth = ctx.requireAuth()
        ctx.authorizeResource(IdResources.policy(policy), IdActions.UPDATE_POLICY)

        val payload = runCatching { ctx.bodyAsClass(UpdatePolicyPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val previousName = policy.name
        val previousDescription = policy.description
        val previousDocument = policy.document

        policyRepository.update(policy) {
            payload.name?.trim()?.takeUnless { it.isEmpty() }?.let { this.name = it }
            payload.description?.trim()?.let { this.description = it.takeUnless { it.isEmpty() } }
            payload.document?.let {
                this.document = it
            }
        }

        val updated = policyRepository.findById(policyId)!!

        eventDispatcher.dispatch(
            PolicyUpdatedEvent(
                principalId = auth.principalId,
                action = IdActions.UPDATE_POLICY,
                resource = IdResources.policy(updated).toString(),
                timestamp = Instant.now(),
                policyId = updated.id,
                previousName = previousName,
                newName = updated.name,
                previousDescription = previousDescription,
                newDescription = updated.description,
                previousDocument = previousDocument,
                newDocument = updated.document,
            )
        )

        ctx.json(updated.toResponse())
    }

    @Delete("/api/v1/policies/{policyId}")
    fun deletePolicy(ctx: Context, @Param policyId: UUID) {
        val policy = policyRepository.findById(policyId)
            ?: throw NotFoundResponse("Policy not found")

        val auth = ctx.requireAuth()
        ctx.authorizeResource(IdResources.policy(policy), IdActions.DELETE_POLICY)

        val policyIdSnapshot = policy.id
        val policyNameSnapshot = policy.name
        val policyDocumentSnapshot = policy.document
        val policyResource = IdResources.policy(policy).toString()
        policyRepository.delete(policy)

        eventDispatcher.dispatch(
            PolicyDeletedEvent(
                principalId = auth.principalId,
                action = IdActions.DELETE_POLICY,
                resource = policyResource,
                timestamp = Instant.now(),
                policyId = policyIdSnapshot,
                policyName = policyNameSnapshot,
                policyDocument = policyDocumentSnapshot
            )
        )

        ctx.status(200)
    }

    // ----- Policy assignments -----

    @Get("/api/v1/policies/{policyId}/assignments")
    fun listPolicyAssignments(ctx: Context, @Param policyId: UUID) {
        ctx.requireAuth()

        val policy = policyRepository.findById(policyId)
            ?: throw NotFoundResponse("Policy not found")

        ctx.authorizeResource(IdResources.policy(policy), IdActions.LIST_POLICY_ASSIGNMENTS)

        val assignments = policyAssignmentRepository.findByPolicy(policy)
        ctx.json(assignments.map { it.toResponse() })
    }

    @Get("/api/v1/accounts/{accountId}/policies")
    fun listAccountPolicies(ctx: Context, @Param accountId: UUID) {
        val auth = ctx.requireAuth()

        val account = accountRepository.findById(accountId)
            ?: throw NotFoundResponse("Account not found")

        // Fast path: session owner always has access to their own account's policies
        if (auth !is PrincipalAuthorizationContext.SessionAuth || auth.session.account.id != accountId) {
            ctx.authorizeResource(IdResources.account(accountId), IdActions.LIST_ACCOUNT_POLICIES)
        }

        val assignments = policyAssignmentRepository.findByAccount(account)
        ctx.json(assignments.map { it.toResponse() })
    }

    @Get("/api/v1/clients/{clientId}/policies")
    fun listClientPolicies(ctx: Context, @Param clientId: UUID) {
        val auth = ctx.requireAuth()

        val client = clientRepository.findById(clientId)
            ?: throw NotFoundResponse("Client not found")

        // Clients can always see their own policy assignments
        if (auth.principalId != clientId) {
            ctx.requireSession()
            ctx.authorizeResource(IdResources.client(clientId), IdActions.LIST_CLIENT_POLICIES)
        }

        val assignments = policyAssignmentRepository.findByClient(client)
        ctx.json(assignments.map { it.toResponse() })
    }

    @Get("/api/v1/roles/{roleId}/policies")
    fun listRolePolicies(ctx: Context, @Param roleId: UUID) {
        ctx.requireAuth()

        val role = roleRepository.findById(roleId)
            ?: throw NotFoundResponse("Role not found")

        ctx.authorizeResource(IdResources.role(role), IdActions.LIST_ROLE_POLICIES)

        val assignments = policyAssignmentRepository.findByRole(role)
        ctx.json(assignments.map { it.toResponse() })
    }

    @Post("/api/v1/policies/{policyId}/assignments")
    fun createPolicyAssignment(ctx: Context, @Param policyId: UUID) {
        val session = ctx.requireSession()

        val policy = policyRepository.findById(policyId)
            ?: throw NotFoundResponse("Policy not found")

        ctx.authorizeResource(IdResources.policy(policy), IdActions.ASSIGN_POLICY)

        val payload = runCatching { ctx.bodyAsClass(CreatePolicyAssignmentPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val targets = listOfNotNull(payload.account_id, payload.client_id, payload.role_id)
        if (targets.size != 1)
            throw BadRequestResponse("Exactly one of account_id, client_id, or role_id must be provided")

        val account: IAccount? = payload.account_id?.let {
            accountRepository.findById(it) ?: throw NotFoundResponse("Account not found")
        }
        val client: IClient? = payload.client_id?.let {
            clientRepository.findById(it) ?: throw NotFoundResponse("Client not found")
        }
        val role: IRole? = payload.role_id?.let {
            roleRepository.findById(it) ?: throw NotFoundResponse("Role not found")
        }

        val assignment = policyAssignmentRepository.create {
            this.policy = policy
            this.account = account
            this.client = client
            this.role = role
            this.expiresAt = payload.expires_at
        }

        eventDispatcher.dispatch(
            PolicyAssignedEvent(
                principalId = session.principalId,
                action = IdActions.ASSIGN_POLICY,
                resource = IdResources.policy(policy).toString(),
                timestamp = Instant.now(),
                assignmentId = assignment.id,
                policyId = policy.id,
                policyName = policy.name,
                accountId = account?.id,
                accountEmail = account?.email,
                clientId = client?.id,
                clientName = client?.fqdn,
                roleId = role?.id,
                roleName = role?.name,
                expiresAt = assignment.expiresAt,
            )
        )

        ctx.status(201)
        ctx.json(assignment.toResponse())
    }

    @Delete("/api/v1/policies/{policyId}/assignments/{assignmentId}")
    fun deletePolicyAssignment(ctx: Context, @Param policyId: UUID, @Param assignmentId: UUID) {
        val session = ctx.requireSession()

        val policy = policyRepository.findById(policyId)
            ?: throw NotFoundResponse("Policy not found")

        val assignment = policyAssignmentRepository.findById(assignmentId)
            ?: throw NotFoundResponse("Assignment not found")

        if (assignment.policy.id != policy.id)
            throw BadRequestResponse("Assignment does not belong to the specified policy")

        ctx.authorizeResource(IdResources.policy(policy), IdActions.UNASSIGN_POLICY)

        val accountId = assignment.account?.id
        val accountEmail = assignment.account?.email
        val clientId = assignment.client?.id
        val clientName = assignment.client?.fqdn
        val roleId = assignment.role?.id
        val roleName = assignment.role?.name

        val assignmentIdSnapshot = assignment.id
        val policyNameSnapshot = policy.name
        val policyResource = IdResources.policy(policy).toString()

        policyAssignmentRepository.delete(assignment)

        eventDispatcher.dispatch(
            PolicyUnassignedEvent(
                principalId = session.principalId,
                action = IdActions.UNASSIGN_POLICY,
                resource = policyResource,
                timestamp = Instant.now(),
                assignmentId = assignmentIdSnapshot,
                policyId = policyId,
                policyName = policyNameSnapshot,
                accountId = accountId,
                accountEmail = accountEmail,
                clientId = clientId,
                clientName = clientName,
                roleId = roleId,
                roleName = roleName,
            )
        )

        ctx.status(200)
    }
}
