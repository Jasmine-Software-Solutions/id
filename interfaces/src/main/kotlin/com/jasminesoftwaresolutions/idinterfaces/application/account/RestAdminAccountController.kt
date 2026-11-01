package com.jasminesoftwaresolutions.idinterfaces.application.account

import com.jasminesoftwaresolutions.id.domain.events.*
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITOTPConfigurationRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.ITOTPService
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.CompiledResourcePath
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.http.*
import java.util.*

/**
 * Admin-level account management: list accounts within a tenant, disable member TOTP, etc.
 */
class RestAdminAccountController(
    private val accountRepository: IAccountRepository,
    private val tenantMembershipRepository: ITenantMembershipRepository,
    private val totpConfigurationRepository: ITOTPConfigurationRepository,
    private val totpService: ITOTPService,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
    private val eventDispatcher: IEventDispatcher,
) {
    data class AccountSummary(
        val id: UUID,
        val email: String,
        val firstName: String,
        val lastName: String,
        val totpEnabled: Boolean,
        val membershipId: UUID?,
    )

    private fun Context.requireSession(): PrincipalAuthorizationContext.SessionAuth {
        val auth = authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
        return auth as? PrincipalAuthorizationContext.SessionAuth ?: throw ForbiddenResponse()
    }

    /** Auth check against a resource with action. */
    private fun Context.authorizeResource(resource: CompiledResourcePath, action: String) {
        val auth = requireSession()
        val allowed = evaluationService.evaluate(auth.principalId, action, resource).get()
        if (!allowed) throw ForbiddenResponse("Insufficient privileges: $action")
    }

    /**
     * List accounts that are members of the given tenant, or all accounts
     * when no tenant is specified (platform scope).
     */
    @Get("/api/v1/admin/accounts")
    fun listAccounts(ctx: Context) {
        ctx.requireSession()

        val tenantId = ctx.queryParam("tenant_id")?.let { UUID.fromString(it) }

        if (tenantId == null) {
            // Platform scope: list all accounts
            ctx.authorizeResource(IdResources.platform(), IdActions.LIST_ACCOUNTS)
            val accounts = accountRepository.entries()
            ctx.json(accounts.map { it.toSummary(null) })
            return
        }

        // Scoped: list memberships and their accounts
        ctx.authorizeResource(IdResources.tenant(tenantId), IdActions.LIST_ACCOUNTS)

        val memberships = tenantMembershipRepository.findByTenant(tenantId)
        ctx.json(memberships.map { m -> m.account.toSummary(m) })
    }

    /**
     * Disable TOTP for an account via their membership.
     * Resource: id.jasmine.software://tenant:{t}/membership:{m}
     * Action:   id.jasmine.software:DisableMemberTOTP
     */
    @Post("/api/v1/admin/accounts/{accountId}/disable-totp")
    fun disableMemberTOTP(ctx: Context, @Param accountId: UUID) {
        ctx.requireSession()

        val membershipId = ctx.queryParam("membership_id")?.let { UUID.fromString(it) }
            ?: throw BadRequestResponse("Missing membership_id")

        val membership = tenantMembershipRepository.findById(membershipId)
            ?: throw NotFoundResponse("Membership not found")

        if (membership.account.id != accountId)
            throw BadRequestResponse("Membership does not belong to this account")

        ctx.authorizeResource(
            IdResources.membership(membership.tenant.id, membershipId),
            IdActions.UPDATE_MEMBER_SECURITY,
        )

        totpService.disable(membership.account)

        val auth = ctx.requireSession()
        eventDispatcher.dispatch(MemberTOTPDisabledEvent(
            principalId = auth.principalId,
            action = IdActions.UPDATE_MEMBER_SECURITY,
            resource = IdResources.membership(membership.tenant.id, membershipId).toString(),
            timestamp = java.time.Instant.now(),
            membershipId = membershipId,
            accountId = accountId,
            accountEmail = membership.account.email,
        ))

        ctx.status(200)
    }

    private fun IAccount.toSummary(membership: ITenantMembership?): AccountSummary {
        val config = totpConfigurationRepository.findByAccount(this)
        return AccountSummary(
            id = id,
            email = email,
            firstName = firstName,
            lastName = lastName,
            totpEnabled = config.enabled,
            membershipId = membership?.id,
        )
    }
}
