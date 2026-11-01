package com.jasminesoftwaresolutions.idinterfaces.application.account

import com.jasminesoftwaresolutions.id.domain.events.*
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISetTOTPConfiguration
import com.jasminesoftwaresolutions.id.domain.repositories.*
import com.jasminesoftwaresolutions.id.domain.services.accounts.IEmailChangeService
import com.jasminesoftwaresolutions.id.domain.services.accounts.ITOTPService
import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinRequestAuthenticationService
import com.jasminesoftwaresolutions.idinterfaces.services.auth.PrincipalAuthorizationContext
import com.jasminesoftwaresolutions.idinterfaces.services.auth.ScopedListHelper
import io.javalin.community.routing.annotations.*
import io.javalin.http.*
import java.time.Instant
import java.util.*

class RestAccountController(
    private val accountRepository: IAccountRepository,
    private val sessionRepository: ISessionRepository,
    private val passwordRepository: IPasswordRepository,
    private val totpService: ITOTPService,
    private val totpConfigurationRepository: ITOTPConfigurationRepository,
    private val roleRepository: IRoleRepository,
    private val roleAssignmentRepository: IRoleAssignmentRepository,
    private val tenantRepository: ITenantRepository,
    private val tenantMembershipRepository: ITenantMembershipRepository,
    private val policyAssignmentRepository: IPolicyAssignmentRepository,
    private val membershipInvitationRepository: IMembershipInvitationRepository,
    private val membershipRepository: ITenantMembershipRepository,
    private val authorizationService: JavalinRequestAuthenticationService,
    private val evaluationService: IAuthorizationService,
    private val scopedListHelper: ScopedListHelper,
    private val clientRepository: IClientRepository,
    private val emailChangeService: IEmailChangeService,
    private val emailChangeRepository: IEmailChangeRepository,
    private val externalBaseUrl: String,
    private val eventDispatcher: IEventDispatcher,
) {
    data class AccountResponse(
        val id: UUID,
        val email: String,
        val firstName: String,
        val lastName: String,
        val emailVerified: Boolean,
    )

    data class SessionResponse(
        val id: UUID,
        val userAgent: String?,
        val ipAddress: String?,
        val createdAt: Instant,
        val expiresAt: Instant,
        val isCurrent: Boolean,
    )

    data class RoleResponse(
        val id: UUID,
        val name: String,
        /** The tenant this role is scoped to, or `null` for platform scope. */
        val tenantId: UUID?,
    )

    data class PolicyAssignmentResponse(
        val id: UUID,
        val policyId: UUID,
        val policyName: String?,
        val expiresAt: Instant?,
    )

    data class MembershipResponse(
        val id: UUID,
        val tenantId: UUID,
        val tenantName: String?,
    )

    data class CreateAccountPayload(
        val email: String?,
        val firstName: String?,
        val lastName: String?,
        val password: String?,
        val invitation_id: UUID?,
        val invitation_token: String?,
    )

    data class UpdateProfilePayload(
        val firstName: String?,
        val lastName: String?,
    )

    data class StartEmailChangePayload(
        val newEmail: String,
    )

    data class EmailChangeStatusResponse(
        val pending: Boolean,
        val newEmail: String?,
    )

    data class UpdatePasswordPayload(
        val password: String?,
    )

    data class TOTPResponse(
        val enabled: Boolean,
        val confirmed: Boolean?,
        val qrCodeUri: String?,
    )

    private fun IAccount.toResponse() = AccountResponse(
        id = id, email = email, firstName = firstName, lastName = lastName,
        emailVerified = emailVerified,
    )

    /** Authenticate via session cookie and return the session auth. */
    private fun Context.requireSession(): PrincipalAuthorizationContext.SessionAuth {
        val auth = authorizationService.authenticate(this) ?: throw UnauthorizedResponse()
        return auth as? PrincipalAuthorizationContext.SessionAuth ?: throw ForbiddenResponse()
    }

    /**
     * Authenticate from whichever credential the request carries — a session
     * cookie or a Bearer token — and return the principal context.
     *
     * The Bearer path is what lets a product backend proxy a route on the
     * user's behalf: it holds the user's delegated access token, not their
     * browser's session cookie, and the token's subject is the same principal
     * either way, so the grant evaluation that follows is identical.
     */
    private fun Context.requireAuth(): PrincipalAuthorizationContext =
        authorizationService.authenticate(this) ?: throw UnauthorizedResponse()

    /**
     * Authenticate the request and verify the principal may perform [action] on the
     * account identified by [accountId].
     *
     * Session-authenticated callers are granted immediate access to their own account.
     * All other requestors (token auth, cross-account session access) are evaluated
     * through the Witchwood evaluation service.
     */
    private fun Context.requireAccountAccess(accountId: UUID, action: String): IAccount {
        val account = accountRepository.findById(accountId)
            ?: throw NotFoundResponse("Account not found")

        val auth = authorizationService.authenticate(this) ?: throw UnauthorizedResponse()

        // Fast path: the session owner always has access to their own account.
        if (auth is PrincipalAuthorizationContext.SessionAuth && auth.session.account.id == accountId)
            return account

        val resource = IdResources.account(accountId)
        val allowed = evaluationService.evaluate(auth.principalId, action, resource).get()

        if (!allowed) throw ForbiddenResponse("Insufficient privileges: $action on account $accountId")

        return account
    }

    // ----- Profile -----

    @Get("/api/v1/accounts/me")
    fun me(ctx: Context) {
        val auth = authorizationService.authenticate(ctx) ?: throw UnauthorizedResponse()
        when (auth) {
            is PrincipalAuthorizationContext.SessionAuth -> ctx.json(auth.session.account.toResponse())
            is PrincipalAuthorizationContext.TokenAuth -> {
                val account = accountRepository.findById(auth.principalId)
                    ?: throw NotFoundResponse("Account not found")
                ctx.json(account.toResponse())
            }
        }
    }

    @Get("/api/v1/entities/accounts")
    fun listAccounts(ctx: Context) {
        val auth = authorizationService.authenticate(ctx) ?: throw UnauthorizedResponse()

        val all = accountRepository.entries()
        val visible = scopedListHelper.filterVisible(
            principalId = auth.principalId,
            action = IdActions.LIST_ACCOUNTS,
            entities = all,
            resourceFn = { IdResources.account(it.id) },
        )

        eventDispatcher.dispatch(
            AccountListedEvent(
                principalId = auth.principalId,
                action = IdActions.LIST_ACCOUNTS,
                resource = IdResources.platform().toString(),
                timestamp = Instant.now(),
                visibleAccountIds = visible.map { it.id },
                totalCount = all.count(),
            )
        )

        ctx.json(visible.map { mapOf("id" to it.id, "name" to it.email) })
    }

    @Get("/api/v1/accounts/{id}")
    fun getById(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.GET_ACCOUNT)
        ctx.json(account.toResponse())
    }

    @Post("/api/v1/accounts")
    fun create(ctx: Context) {
        val payload = runCatching { ctx.bodyAsClass(CreateAccountPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val email = payload.email?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing email")
        val firstName = payload.firstName?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing first_name")
        val lastName = payload.lastName?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing last_name")

        val invitationId = payload.invitation_id
        val invitationToken = payload.invitation_token

        // If creating with an invitation token, no auth required — the token proves the invitation
        if (invitationId != null && invitationToken != null) {
            val invitation = membershipInvitationRepository.findById(invitationId)
                ?: throw BadRequestResponse("Invalid invitation")
            if (!invitation.verify(invitationToken))
                throw BadRequestResponse("Invalid invitation token")
            if (invitation.acceptedAt != null)
                throw BadRequestResponse("Invitation already accepted")

            val existing = accountRepository.findByEmail(email)
            val account = existing ?: accountRepository.create {
                this.email = email
                this.firstName = firstName
                this.lastName = lastName
                // Email is verified by virtue of accepting a valid invitation
                this.emailVerified = true
            }

            // Set password if provided and account is new
            if (existing == null && payload.password?.isNotBlank() == true) {
                passwordRepository.create {
                    this.account = account
                    this.password = payload.password.trim()
                }
            }

            // Auto-accept: create membership from invitation
            membershipRepository.create {
                this.account = account
                this.tenant = invitation.tenant
            }
            membershipInvitationRepository.update(invitation) {
                acceptedAt = java.time.Instant.now()
            }

            // Dispatch events — use account's own id as principal since invitation path has no auth
            val now = Instant.now()
            if (existing == null) {
                eventDispatcher.dispatch(
                    AccountCreatedEvent(
                        principalId = account.id,
                        action = IdActions.CREATE_ACCOUNT,
                        resource = IdResources.account(account.id).toString(),
                        timestamp = now,
                        accountId = account.id,
                        email = account.email,
                        firstName = account.firstName,
                        lastName = account.lastName,
                        emailVerified = account.emailVerified,
                        ipAddress = ctx.ip(),
                    )
                )
            }
            eventDispatcher.dispatch(
                InvitationAcceptedEvent(
                    principalId = account.id,
                    action = IdActions.CREATE_ACCOUNT,
                    resource = IdResources.tenant(invitation.tenant.id).toString(),
                    timestamp = now,
                    invitationId = invitation.id,
                    email = account.email,
                    accountId = account.id,
                    tenantId = invitation.tenant.id,
                    tenantName = invitation.tenant.name,
                )
            )

            ctx.status(if (existing == null) 201 else 200)
            ctx.json(account.toResponse())
            return
        }

        // Otherwise, require auth
        val auth = authorizationService.authenticate(ctx) ?: throw UnauthorizedResponse()

        val platformResource = IdResources.platform()
        val allowed = evaluationService.evaluate(auth.principalId, IdActions.CREATE_ACCOUNT, platformResource).get()
        if (!allowed) throw ForbiddenResponse("Insufficient privileges: create account")

        val existing = accountRepository.findByEmail(email)
        val account = existing ?: accountRepository.create {
            this.email = email
            this.firstName = firstName
            this.lastName = lastName
        }

        // Dispatch event for new accounts (not existing)
        if (existing == null) {
            eventDispatcher.dispatch(
                AccountCreatedEvent(
                    principalId = auth.principalId,
                    action = IdActions.CREATE_ACCOUNT,
                    resource = IdResources.account(account.id).toString(),
                    timestamp = Instant.now(),
                    accountId = account.id,
                    email = account.email,
                    firstName = account.firstName,
                    lastName = account.lastName,
                    emailVerified = account.emailVerified,
                    ipAddress = ctx.ip(),
                )
            )
        }

        ctx.status(if (existing == null) 201 else 200)
        ctx.json(account.toResponse())
    }

    @Post("/api/v1/accounts/{id}/profile")
    fun updateProfile(ctx: Context, @Param id: UUID) {
        ctx.requireAccountAccess(id, IdActions.UPDATE_ACCOUNT_PROFILE)

        val account = accountRepository.findById(id)!!
        val previousFirstName = account.firstName
        val previousLastName = account.lastName

        val payload = runCatching { ctx.bodyAsClass(UpdateProfilePayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val newFirstName = payload.firstName?.trim()?.takeUnless { it.isEmpty() }
        val newLastName = payload.lastName?.trim()?.takeUnless { it.isEmpty() }

        accountRepository.update(account) {
            if (newFirstName != null) this.firstName = newFirstName
            if (newLastName != null) this.lastName = newLastName
        }

        val auth = authorizationService.authenticate(ctx)!!
        eventDispatcher.dispatch(
            AccountProfileUpdatedEvent(
                principalId = auth.principalId,
                action = IdActions.UPDATE_ACCOUNT_PROFILE,
                resource = IdResources.account(account.id).toString(),
                timestamp = Instant.now(),
                accountId = account.id,
                previousFirstName = if (newFirstName != null) previousFirstName else null,
                newFirstName = newFirstName,
                previousLastName = if (newLastName != null) previousLastName else null,
                newLastName = newLastName,
            )
        )

        ctx.json(accountRepository.findById(account.id)!!.toResponse())
    }

    // ----- Email Change -----

    @Post("/api/v1/accounts/{id}/email-change")
    fun startEmailChange(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.UPDATE_ACCOUNT_PROFILE)

        val payload = runCatching { ctx.bodyAsClass(StartEmailChangePayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val newEmail = payload.newEmail.trim().takeUnless { it.isBlank() }
            ?: throw BadRequestResponse("Missing new email address")

        if (newEmail == account.email)
            throw BadRequestResponse("New email must be different from current email")

        val existing = accountRepository.findByEmail(newEmail)
        if (existing != null && existing.id != account.id)
            throw BadRequestResponse("Email address is already in use")

        emailChangeService.create(account, newEmail)

        val auth = authorizationService.authenticate(ctx)!!
        eventDispatcher.dispatch(
            AccountEmailChangeStartedEvent(
                principalId = auth.principalId,
                action = IdActions.UPDATE_ACCOUNT_PROFILE,
                resource = IdResources.account(account.id).toString(),
                timestamp = Instant.now(),
                accountId = account.id,
                newEmail = newEmail,
            )
        )

        ctx.status(200)
    }

    @Get("/api/v1/accounts/{id}/email-change/status")
    fun emailChangeStatus(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.GET_ACCOUNT)

        val changes = emailChangeRepository.findByAccount(account)
        val pending = changes.firstOrNull { !it.confirmed && it.decidedAt == null && it.expiresAt > Instant.now() }

        ctx.json(
            EmailChangeStatusResponse(
                pending = pending != null,
                newEmail = pending?.newEmail,
            )
        )
    }

    @Get("/api/v1/accounts/{id}/email-change/verify")
    fun verifyEmailChange(ctx: Context, @Param id: UUID) {
        val changeId = ctx.queryParam("changeId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw BadRequestResponse("Missing or invalid changeId")
        val token = ctx.queryParam("token")
            ?: throw BadRequestResponse("Missing token")

        val account = accountRepository.findById(id)
            ?: throw NotFoundResponse("Account not found")

        val change = emailChangeRepository.findById(changeId)
            ?: throw BadRequestResponse("Invalid change request")

        if (change.account.id != account.id)
            throw BadRequestResponse("Change request does not belong to this account")

        if (change.decidedAt != null)
            throw BadRequestResponse("Change request already processed")

        if (change.expiresAt.isBefore(java.time.Instant.now()))
            throw BadRequestResponse("Verification link expired")

        if (!change.verify(token))
            throw BadRequestResponse("Invalid verification token")

        emailChangeRepository.update(change) {
            this.confirmed = true
            this.decidedAt = java.time.Instant.now()
        }

        val previousEmail = account.email

        accountRepository.update(account) {
            this.email = change.newEmail
            this.emailVerified = true
        }

        // No authenticated principal for email verification; use account's own id
        eventDispatcher.dispatch(
            AccountEmailChangeVerifiedEvent(
                principalId = account.id,
                action = IdActions.UPDATE_ACCOUNT_PROFILE,
                resource = IdResources.account(account.id).toString(),
                timestamp = Instant.now(),
                accountId = account.id,
                previousEmail = previousEmail,
                newEmail = change.newEmail,
            )
        )

        ctx.redirect("${externalBaseUrl.trimEnd('/')}/#/?verified=1")
    }

    @Delete("/api/v1/accounts/{id}")
    fun deleteAccount(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.DELETE_ACCOUNT)
        val auth = authorizationService.authenticate(ctx)!!
        val accountEmail = account.email
        val accountId = account.id

        accountRepository.delete(account)

        eventDispatcher.dispatch(
            AccountDeletedEvent(
                principalId = auth.principalId,
                action = IdActions.DELETE_ACCOUNT,
                resource = IdResources.account(accountId).toString(),
                timestamp = Instant.now(),
                accountId = accountId,
                email = accountEmail,
            )
        )

        ctx.status(200)
    }

    // ----- Password -----

    @Post("/api/v1/accounts/{id}/password")
    fun setPassword(ctx: Context, @Param id: UUID) {
        ctx.requireAccountAccess(id, IdActions.SET_ACCOUNT_PASSWORD)

        val account = accountRepository.findById(id)!!

        val payload = runCatching { ctx.bodyAsClass(UpdatePasswordPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        val newPassword = payload.password?.trim()?.takeUnless { it.isEmpty() }
            ?: throw BadRequestResponse("Missing password")

        val existingPassword = passwordRepository.findByAccount(account).firstOrNull()
        val overwroteExisting = existingPassword != null
        if (existingPassword != null) {
            passwordRepository.update(existingPassword) {
                this.password = newPassword
            }
        } else {
            passwordRepository.create {
                this.account = account
                this.password = newPassword
            }
        }

        val auth = authorizationService.authenticate(ctx)!!
        eventDispatcher.dispatch(
            AccountPasswordSetEvent(
                principalId = auth.principalId,
                action = IdActions.SET_ACCOUNT_PASSWORD,
                resource = IdResources.account(account.id).toString(),
                timestamp = Instant.now(),
                accountId = account.id,
                overwroteExisting = overwroteExisting,
            )
        )

        ctx.status(200)
    }

    // ----- TOTP -----

    @Get("/api/v1/accounts/{id}/totp")
    fun getTOTP(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.GET_ACCOUNT_TOTP)

        val config = totpConfigurationRepository.findByAccount(account)
        val setConfig = config as? ISetTOTPConfiguration

        ctx.json(
            TOTPResponse(
                enabled = config.enabled,
                confirmed = setConfig?.confirmedAt != null,
                qrCodeUri = setConfig?.let { totpService.uri(it) },
            )
        )
    }

    @Post("/api/v1/accounts/{id}/totp/enable")
    fun enableTOTP(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.ENABLE_ACCOUNT_TOTP)
        val config = totpService.enable(account)
        val setConfig = config as? ISetTOTPConfiguration

        val auth = authorizationService.authenticate(ctx)!!
        eventDispatcher.dispatch(
            AccountTOTPEnabledEvent(
                principalId = auth.principalId,
                action = IdActions.ENABLE_ACCOUNT_TOTP,
                resource = IdResources.account(account.id).toString(),
                timestamp = Instant.now(),
                accountId = account.id,
                ipAddress = ctx.ip(),
            )
        )

        ctx.json(
            TOTPResponse(
                enabled = config.enabled,
                confirmed = setConfig?.confirmedAt != null,
                qrCodeUri = setConfig?.let { totpService.uri(it) },
            )
        )
    }

    @Post("/api/v1/accounts/{id}/totp/disable")
    fun disableTOTP(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.DISABLE_ACCOUNT_TOTP)
        totpService.disable(account)

        val config = totpConfigurationRepository.findByAccount(account)

        val auth = authorizationService.authenticate(ctx)!!
        eventDispatcher.dispatch(
            AccountTOTPDisabledEvent(
                principalId = auth.principalId,
                action = IdActions.DISABLE_ACCOUNT_TOTP,
                resource = IdResources.account(account.id).toString(),
                timestamp = Instant.now(),
                accountId = account.id,
                ipAddress = ctx.ip(),
            )
        )

        ctx.json(
            TOTPResponse(
                enabled = config.enabled,
                confirmed = (config as? ISetTOTPConfiguration)?.confirmedAt != null,
                qrCodeUri = null,
            )
        )
    }

    @Post("/api/v1/accounts/{id}/totp/confirm")
    fun confirmTOTP(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.CONFIRM_ACCOUNT_TOTP)

        val code = ctx.formParam("totp")?.trim()?.toIntOrNull()
            ?: throw BadRequestResponse("Invalid TOTP code")

        val config = totpConfigurationRepository.findByAccount(account) as? ISetTOTPConfiguration
            ?: throw BadRequestResponse("TOTP not enabled")

        if (!totpService.verify(config, code).valid) {
            throw BadRequestResponse("Invalid TOTP code")
        }

        totpConfigurationRepository.update(config) {
            (this as ISetTOTPConfiguration).confirmedAt = Instant.now()
        }

        val auth = authorizationService.authenticate(ctx)!!
        eventDispatcher.dispatch(
            AccountTOTPConfirmedEvent(
                principalId = auth.principalId,
                action = IdActions.CONFIRM_ACCOUNT_TOTP,
                resource = IdResources.account(account.id).toString(),
                timestamp = Instant.now(),
                accountId = account.id,
            )
        )

        ctx.status(200)
    }

    // ----- Sessions -----

    @Get("/api/v1/accounts/{id}/sessions")
    fun listSessions(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.LIST_ACCOUNT_SESSIONS)

        val auth = authorizationService.authenticate(ctx)
        val currentSessionId = (auth as? PrincipalAuthorizationContext.SessionAuth)?.session?.id

        val sessions = sessionRepository.findByAccount(account)

        ctx.json(sessions.map { session ->
            SessionResponse(
                id = session.id,
                userAgent = session.userAgent,
                ipAddress = session.ipAddress,
                createdAt = session.createdAt,
                expiresAt = session.expiresAt,
                isCurrent = session.id == currentSessionId,
            )
        })
    }

    @Post("/api/v1/accounts/{account_id}/sessions/{session_id}/revoke")
    fun revokeSession(
        ctx: Context,
        @Param("account_id") accountId: UUID,
        @Param("session_id") sessionId: UUID,
    ) {
        val account = ctx.requireAccountAccess(accountId, IdActions.REVOKE_ACCOUNT_SESSION)

        val session = sessionRepository.findById(sessionId) ?: throw NotFoundResponse()

        if (session.account.id != account.id)
            throw ForbiddenResponse()

        sessionRepository.delete(session)

        val auth = authorizationService.authenticate(ctx)!!
        val currentSessionId = (auth as? PrincipalAuthorizationContext.SessionAuth)?.session?.id
        eventDispatcher.dispatch(
            SessionRevokedEvent(
                principalId = auth.principalId,
                action = IdActions.REVOKE_ACCOUNT_SESSION,
                resource = IdResources.account(account.id).toString(),
                timestamp = Instant.now(),
                sessionId = sessionId,
                accountId = account.id,
                revokedByPrincipalId = auth.principalId,
                wasSelfRevocation = sessionId == currentSessionId,
            )
        )

        ctx.status(200)
    }

    @Post("/api/v1/accounts/{id}/sessions/revoke-all")
    fun revokeAllSessions(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.REVOKE_ACCOUNT_SESSION)

        val auth = authorizationService.authenticate(ctx)
        val currentSessionId = (auth as? PrincipalAuthorizationContext.SessionAuth)?.session?.id

        val sessions = sessionRepository.findByAccount(account)
        var revokedCount = 0
        for (session in sessions) {
            if (session.id != currentSessionId) {
                sessionRepository.delete(session)
                revokedCount++
            }
        }

        eventDispatcher.dispatch(
            AllSessionsRevokedEvent(
                principalId = auth!!.principalId,
                action = IdActions.REVOKE_ACCOUNT_SESSION,
                resource = IdResources.account(account.id).toString(),
                timestamp = Instant.now(),
                accountId = account.id,
                revokedCount = revokedCount,
            )
        )

        ctx.status(200)
    }

    // ----- Roles -----

    @Get("/api/v1/accounts/{id}/roles")
    fun listRoles(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.LIST_ACCOUNT_ROLES)
        val assigned = roleAssignmentRepository.findByAccount(account)

        ctx.json(assigned.map { assignment ->
            val role = assignment.role
            RoleResponse(
                id = role.id,
                name = role.name,
                tenantId = role.tenant?.id,
            )
        })
    }

    // ----- Policies -----

    @Get("/api/v1/accounts/{id}/policies")
    fun listPolicies(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.LIST_ACCOUNT_POLICIES)
        val assignments = policyAssignmentRepository.findByAccount(account)

        ctx.json(assignments.map { assignment ->
            PolicyAssignmentResponse(
                id = assignment.id,
                policyId = assignment.policy.id,
                policyName = assignment.policy.name,
                expiresAt = assignment.expiresAt,
            )
        })
    }

    // ----- Memberships -----

    @Get("/api/v1/accounts/{id}/memberships")
    fun listMemberships(ctx: Context, @Param id: UUID) {
        val account = ctx.requireAccountAccess(id, IdActions.LIST_ACCOUNT_MEMBERSHIPS)
        val memberships = tenantMembershipRepository.findByAccount(account)

        ctx.json(memberships.map { m ->
            MembershipResponse(
                id = m.id,
                tenantId = m.tenant.id,
                tenantName = m.tenant.name,
            )
        })
    }

    // ----- SSO Clients -----

    data class SsoClientResponse(
        val clientId: UUID,
        val fqdn: String,
        val name: String?,
        val logoUri: String?,
        val initiateLoginUri: String,
    )

    @Get("/api/v1/accounts/me/sso-clients")
    fun listSsoClients(ctx: Context) {
        // Bearer as well as cookie: product servers proxy this route with the
        // user's delegated access token, whose subject is the same principal.
        val auth = ctx.requireAuth()

        // Use Witchwood queryResources to find all clients the account can SSO into.
        val visible = scopedListHelper.filterVisible(
            principalId = auth.principalId,
            action = IdActions.GET_SSO_APPLICATION,
            entities = clientRepository.entries(),
            resourceFn = { IdResources.client(it.id) },
        )

        // Only return clients that can actually be launched: an absent
        // initiate_login_uri leaves nowhere to send the browser, and a
        // suspended client must not be offered as a destination.
        val eligible = visible.filter { it.initiateLoginUri != null && !it.suspended }

        ctx.json(eligible.map { client ->
            SsoClientResponse(
                clientId = client.id,
                fqdn = client.fqdn,
                name = client.name,
                logoUri = client.logoUri?.toString(),
                initiateLoginUri = client.initiateLoginUri!!.toString(),
            )
        })
    }

    // ----- SSO Preferences -----

    data class SsoPreferencesPayload(
        val favorites: List<String>,
        val order: List<String>,
    )

    @Get("/api/v1/accounts/me/sso-preferences")
    fun getSsoPreferences(ctx: Context) {
        val auth = ctx.requireSession()
        val account = auth.session.account
        // Return stored preferences or defaults.
        ctx.json(
            SsoPreferencesPayload(
                favorites = account.ssoFavoriteClientIds,
                order = account.ssoClientOrder,
            )
        )
    }

    @Put("/api/v1/accounts/me/sso-preferences")
    fun saveSsoPreferences(ctx: Context) {
        val auth = ctx.requireSession()
        val account = auth.session.account

        val payload = runCatching { ctx.bodyAsClass(SsoPreferencesPayload::class.java) }.getOrNull()
            ?: throw BadRequestResponse("Invalid payload")

        accountRepository.update(account) {
            this.ssoFavoriteClientIds = payload.favorites
            this.ssoClientOrder = payload.order
        }

        ctx.status(200)
    }
}
