package com.jasminesoftwaresolutions.idinterfaces.application.tenant

import com.jasminesoftwaresolutions.id.domain.events.*
import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.tenant.IMembershipInvitation
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IMembershipInvitationRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.http.*
import java.time.Instant
import java.util.*

class RestMembershipInvitationController(
    private val invitationRepository: IMembershipInvitationRepository,
    private val tenantRepository: ITenantRepository,
    private val accountRepository: IAccountRepository,
    private val membershipRepository: ITenantMembershipRepository,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
    private val eventDispatcher: IEventDispatcher,
) {
    data class InvitePayload(
        val email: String?,
    )

    data class InvitationResponse(
        val id: UUID,
        val email: String,
        val tenantId: UUID,
        val tenantName: String?,
        val acceptedAt: Long?,
        val createdAt: Long,
    )

    private fun IMembershipInvitation.toResponse() = InvitationResponse(
        id = id,
        email = email,
        tenantId = tenant.id,
        tenantName = tenant.name,
        acceptedAt = acceptedAt?.epochSecond,
        createdAt = createdAt.epochSecond,
    )

    private fun Context.requireSession(): PrincipalAuthorizationContext.SessionAuth {
        val auth = authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
        return auth as? PrincipalAuthorizationContext.SessionAuth ?: throw ForbiddenResponse()
    }

    // ----- Invite account to tenant -----

    @Post("/api/v1/tenants/{tenantId}/invitations")
    fun inviteToTenant(ctx: Context, @Param tenantId: UUID) {
        val auth = ctx.requireSession()
        val tenant = tenantRepository.findById(tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        // Authorize: InviteAccount on the tenant
        val allowed = evaluationService.evaluate(auth.principalId, IdActions.INVITE_ACCOUNT, IdResources.tenant(tenantId)).get()
        if (!allowed) throw ForbiddenResponse("Insufficient privileges")

        val payload = runCatching { ctx.bodyAsClass(InvitePayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val email = payload.email?.trim()?.lowercase().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing email")

        // Check for existing pending invitation
        val existing = invitationRepository.findPendingByEmailAndTenant(email, tenantId)
        if (existing.isNotEmpty()) {
            ctx.json(existing.first().toResponse())
            return
        }

        // Generate secure token
        val token = SecureToken()

        val invitation = invitationRepository.create {
            this.email = email
            this.tenant = tenant
            this.token = token
        }

        eventDispatcher.dispatch(InvitationCreatedEvent(
            principalId = auth.principalId,
            action = IdActions.INVITE_ACCOUNT,
            resource = IdResources.tenant(tenantId).toString(),
            timestamp = Instant.now(),
            invitationId = invitation.id,
            email = email,
            tenantId = tenantId,
            tenantName = tenant.name,
        ))

        ctx.status(201)
        ctx.json(invitation.toResponse())
    }

    // ----- Get invitation by ID -----

    @Get("/api/v1/invitations/{invitationId}")
    fun getInvitation(ctx: Context, @Param invitationId: UUID) {
        val token = ctx.queryParam("token")
            ?: throw BadRequestResponse("Missing token")

        val invitation = invitationRepository.findById(invitationId)
            ?: throw NotFoundResponse("Invitation not found")

        if (!invitation.verify(token))
            throw BadRequestResponse("Invalid token")

        ctx.json(invitation.toResponse())
    }

    // ----- Accept invitation -----

    @Post("/api/v1/invitations/{invitationId}/accept")
    fun acceptInvitation(ctx: Context, @Param invitationId: UUID) {
        val token = ctx.queryParam("token")
            ?: throw BadRequestResponse("Missing token")

        // Must be authenticated to know which account is accepting
        val auth = authorizationService.authenticate(ctx) ?: throw UnauthorizedResponse()
        val sessionAuth = auth as? PrincipalAuthorizationContext.SessionAuth
            ?: throw ForbiddenResponse("Must be signed in to accept an invitation")

        val invitation = invitationRepository.findById(invitationId)
            ?: throw NotFoundResponse("Invitation not found")

        if (!invitation.verify(token))
            throw BadRequestResponse("Invalid token")

        if (invitation.acceptedAt != null)
            throw BadRequestResponse("Invitation already accepted")

        val account = sessionAuth.session.account

        // Authorize: AcceptInvitation on the account
        val allowed = evaluationService.evaluate(account.id, IdActions.ACCEPT_INVITATION, IdResources.account(account.id)).get()
        if (!allowed) throw ForbiddenResponse("Insufficient privileges")

        // Create membership
        membershipRepository.create {
            this.account = account
            this.tenant = invitation.tenant
        }

        // Mark invitation as accepted
        invitationRepository.update(invitation) {
            acceptedAt = Instant.now()
        }

        eventDispatcher.dispatch(InvitationAcceptedEvent(
            principalId = sessionAuth.session.account.id,
            action = IdActions.ACCEPT_INVITATION,
            resource = IdResources.account(account.id).toString(),
            timestamp = Instant.now(),
            invitationId = invitation.id,
            email = invitation.email,
            accountId = account.id,
            tenantId = invitation.tenant.id,
            tenantName = invitation.tenant.name,
        ))

        ctx.status(200)
        ctx.json(invitationRepository.findById(invitationId)!!.toResponse())
    }

    // ----- List invitations for a tenant -----

    @Get("/api/v1/tenants/{tenantId}/invitations")
    fun listTenantInvitations(ctx: Context, @Param tenantId: UUID) {
        ctx.requireSession()
        val tenant = tenantRepository.findById(tenantId)
            ?: throw NotFoundResponse("Tenant not found")

        // Authorization will be handled per-invitation or scoped differently in the future
        ctx.json(emptyList<InvitationResponse>())
    }
}
