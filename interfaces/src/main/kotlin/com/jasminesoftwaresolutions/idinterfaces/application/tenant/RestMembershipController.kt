package com.jasminesoftwaresolutions.idinterfaces.application.tenant

import com.jasminesoftwaresolutions.id.domain.events.*
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import io.javalin.community.routing.annotations.Delete
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.http.*
import java.time.Instant
import java.util.*

class RestMembershipController(
    private val tenantMembershipRepository: ITenantMembershipRepository,
    private val tenantRepository: ITenantRepository,
    private val accountRepository: IAccountRepository,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
    private val eventDispatcher: IEventDispatcher,
) {
    // ----- Response types -----

    data class MembershipResponse(
        val id: UUID,
        val accountId: UUID,
        val accountEmail: String?,
        val tenantId: UUID,
        val tenantName: String?,
    )

    // ----- Payload types -----

    data class CreateMembershipPayload(
        val account_id: UUID?,
    )

    data class UpdateMemberProfilePayload(
        val first_name: String?,
        val last_name: String?,
        val email: String?,
    )

    // ----- Helpers -----

    private fun ITenantMembership.toResponse() = MembershipResponse(
        id = id,
        accountId = account.id,
        accountEmail = account.email,
        tenantId = tenant.id,
        tenantName = tenant.name,
    )

    /** Require valid session, throws on failure. */
    private fun Context.requireSession(): PrincipalAuthorizationContext.SessionAuth {
        val auth = authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
        return auth as? PrincipalAuthorizationContext.SessionAuth ?: throw ForbiddenResponse()
    }

    /** Authorize a membership action on a tenant. */
    private fun Context.authorizeMembershipAction(action: String, tenantId: UUID) {
        val auth = requireSession()
        val resource = IdResources.tenant(tenantId)
        val allowed = evaluationService.evaluate(auth.principalId, action, resource).get()
        if (!allowed) throw ForbiddenResponse("Insufficient privileges: $action on $resource")
    }

    private fun Context.authorizePlatformAction(action: String) {
        val auth = requireSession()
        val resource = IdResources.platform()
        val allowed = evaluationService.evaluate(auth.principalId, action, resource).get()
        if (!allowed) throw ForbiddenResponse("Insufficient privileges: $action on $resource")
    }

    // ----- Listing memberships -----

    @Get("/api/v1/tenants/{tenantId}/memberships")
    fun listTenantMemberships(ctx: Context, @Param tenantId: UUID) {
        ctx.authorizeMembershipAction(IdActions.LIST_MEMBERSHIPS, tenantId)

        val tenant = tenantRepository.findById(tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        val memberships = tenantMembershipRepository.findByTenant(tenant)
        ctx.json(memberships.map { it.toResponse() })
    }

    // ----- Getting a single membership -----

    @Get("/api/v1/memberships/{membershipId}")
    fun getMembership(ctx: Context, @Param membershipId: UUID) {
        val membership = tenantMembershipRepository.findById(membershipId)
            ?: throw NotFoundResponse("Membership not found")

        ctx.authorizeMembershipAction(IdActions.GET_MEMBERSHIP, membership.tenant.id)
        ctx.json(membership.toResponse())
    }

    // ----- Creating memberships -----

    @Post("/api/v1/tenants/{tenantId}/memberships")
    fun createTenantMembership(ctx: Context, @Param tenantId: UUID) {
        ctx.authorizePlatformAction(IdActions.CREATE_MEMBERSHIP)

        val tenant = tenantRepository.findById(tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        val payload = runCatching { ctx.bodyAsClass(CreateMembershipPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val accountId = payload.account_id
            ?: throw BadRequestResponse("Missing account_id")

        val account = accountRepository.findById(accountId)
            ?: throw NotFoundResponse("Account not found")

        val membership = tenantMembershipRepository.create {
            this.account = account
            this.tenant = tenant
        }

        val auth = ctx.requireSession()
        eventDispatcher.dispatch(
            MembershipCreatedEvent(
                principalId = auth.principalId,
                action = IdActions.CREATE_MEMBERSHIP,
                resource = IdResources.membership(membership.tenant.id, membership.id).toString(),
                timestamp = Instant.now(),
                membershipId = membership.id,
                accountId = account.id,
                accountEmail = account.email,
                tenantId = tenant.id,
                tenantName = tenant.name,
            )
        )

        ctx.status(201)
        ctx.json(membership.toResponse())
    }

    // ----- Updating member profile -----

    @Post("/api/v1/memberships/{membershipId}/profile")
    fun updateMemberProfile(ctx: Context, @Param membershipId: UUID) {
        val membership = tenantMembershipRepository.findById(membershipId)
            ?: throw NotFoundResponse("Membership not found")

        ctx.authorizeMembershipAction(IdActions.UPDATE_MEMBER_PROFILE, membership.tenant.id)

        val payload = runCatching { ctx.bodyAsClass(UpdateMemberProfilePayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val account = membership.account
        val previousFirstName = account.firstName
        val previousLastName = account.lastName
        val previousEmail = account.email
        val newFirstName = payload.first_name?.trim()?.takeUnless { it.isEmpty() }
        val newLastName = payload.last_name?.trim()?.takeUnless { it.isEmpty() }
        val newEmail = payload.email?.trim()?.takeUnless { it.isEmpty() }

        accountRepository.update(account) {
            if (newFirstName != null) this.firstName = newFirstName
            if (newLastName != null) this.lastName = newLastName
            if (newEmail != null) this.email = newEmail
        }

        val auth = ctx.requireSession()
        eventDispatcher.dispatch(
            MemberProfileUpdatedEvent(
                principalId = auth.principalId,
                action = IdActions.UPDATE_MEMBER_PROFILE,
                resource = IdResources.membership(membership.tenant.id, membership.id).toString(),
                timestamp = Instant.now(),
                membershipId = membership.id,
                accountId = account.id,
                previousFirstName = previousFirstName,
                newFirstName = newFirstName,
                previousLastName = previousLastName,
                newLastName = newLastName,
                previousEmail = previousEmail,
                newEmail = newEmail,
            )
        )

        ctx.json(membership.toResponse())
    }

    // ----- Deleting memberships -----

    @Delete("/api/v1/memberships/{membershipId}")
    fun deleteMembership(ctx: Context, @Param membershipId: UUID) {
        val auth = ctx.requireSession()

        val membership = tenantMembershipRepository.findById(membershipId)
            ?: throw NotFoundResponse("Membership not found")

        ctx.authorizeMembershipAction(IdActions.DELETE_MEMBERSHIP, membership.tenant.id)

        val membershipIdCaptured = membership.id
        val accountIdCaptured = membership.account.id
        val accountEmailCaptured = membership.account.email
        val tenantIdCaptured = membership.tenant.id
        val tenantNameCaptured = membership.tenant.name

        tenantMembershipRepository.delete(membership)

        eventDispatcher.dispatch(
            MembershipDeletedEvent(
                principalId = auth.principalId,
                action = IdActions.DELETE_MEMBERSHIP,
                resource = IdResources.membership(tenantIdCaptured, membershipIdCaptured).toString(),
                timestamp = Instant.now(),
                membershipId = membershipIdCaptured,
                accountId = accountIdCaptured,
                accountEmail = accountEmailCaptured,
                tenantId = tenantIdCaptured,
                tenantName = tenantNameCaptured,
            )
        )

        ctx.status(200)
    }
}
