package com.jasminesoftwaresolutions.idinterfaces.user.account

import com.jasminesoftwaresolutions.id.domain.models.account.IHashedPassword
import com.jasminesoftwaresolutions.id.domain.models.account.IHashedSession
import com.jasminesoftwaresolutions.id.domain.models.authorization.*
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.idinterfaces.renderWithContext
import com.jasminesoftwaresolutions.idinterfaces.services.account.*
import com.jasminesoftwaresolutions.idinterfaces.services.auth.JavalinAuthorizationService
import io.javalin.community.routing.annotations.Get
import io.javalin.community.routing.annotations.Param
import io.javalin.community.routing.annotations.Post
import io.javalin.http.*
import java.util.*

class AjaxAccountController(
    private val authorizationService: JavalinAuthorizationService,
    private val accountService: AccountControllerService,
    private val sessionService: SessionControllerService<IHashedSession>,
    private val totpConfigurationService: TOTPConfigurationControllerService,
    private val passwordService: PasswordControllerService<IHashedPassword>,
    private val platformRoleService: PlatformRoleControllerService,
    private val tenantMembershipService: TenantMembershipControllerService<ITenantMembership, ITenant>
) {
    private fun Context.requireSession(): ISessionAuthorizationContext {
        val authentication = authorizationService.authenticate(this)
            ?: throw UnauthorizedResponse()

        if (authentication !is ISessionAuthorizationContext)
            throw ForbiddenResponse()

        return authentication
    }

    private fun parseUuid(name: String, value: String?): UUID {
        val parsed = value?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return parsed ?: throw BadRequestResponse("Invalid $name")
    }

    private fun parseRoleId(value: String?): String {
        return value?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing role id")
    }

    private fun redirectToAccount(ctx: Context, accountId: UUID?, flash: String? = null) {
        val path = if (accountId == null) "/account" else "/accounts/$accountId"
        ctx.redirect(path)
    }

    private fun renderAccount(ctx: Context, authentication: ISessionAuthorizationContext, accountId: UUID?) {
        val accountResult = accountService.get(authentication, accountId)
        val account = when (accountResult) {
            is AccountControllerService.GetAccountResult.Success -> accountResult.account.account
            is AccountControllerService.GetAccountResult.Forbidden -> throw ForbiddenResponse()
            is AccountControllerService.GetAccountResult.NotFound -> throw NotFoundResponse("Account not found")
            else -> throw BadRequestResponse()
        }

        val targetAccountId = account.id
        val isSelf = targetAccountId == authentication.account.id

        val sessions = when (val result = sessionService.getAll(authentication, targetAccountId)) {
            is SessionControllerService.GetSessionsResult.Success -> result.sessions
            is SessionControllerService.GetSessionsResult.Unauthorized -> throw UnauthorizedResponse()
            is SessionControllerService.GetSessionsResult.Forbidden -> throw ForbiddenResponse()
            is SessionControllerService.GetSessionsResult.NotFound -> throw NotFoundResponse("Account not found")
            else -> throw BadRequestResponse()
        }

        val password = when (val result = passwordService.last(authentication, targetAccountId)) {
            is PasswordControllerService.LastResult.Success -> result.password
            is PasswordControllerService.LastResult.Unauthorized -> throw UnauthorizedResponse()
            is PasswordControllerService.LastResult.Forbidden -> throw ForbiddenResponse()
            is PasswordControllerService.LastResult.NotFound -> throw NotFoundResponse("Account not found")
            else -> throw BadRequestResponse()
        }

        val (totpConfiguration, totpQrCodeUri) = when (val result = totpConfigurationService.get(authentication, targetAccountId)) {
            is TOTPConfigurationControllerService.GetResult.Success -> result.configuration to result.qrCodeUri
            is TOTPConfigurationControllerService.GetResult.Unauthorized -> throw UnauthorizedResponse()
            is TOTPConfigurationControllerService.GetResult.Forbidden -> throw ForbiddenResponse()
            is TOTPConfigurationControllerService.GetResult.NotFound -> throw NotFoundResponse("Account not found")
            else -> throw BadRequestResponse()
        }

        val memberships = when (val result = tenantMembershipService.read(authentication, targetAccountId)) {
            is TenantMembershipControllerService.ReadResult.Success -> result.memberships
            is TenantMembershipControllerService.ReadResult.Unauthorized -> throw UnauthorizedResponse()
            is TenantMembershipControllerService.ReadResult.Forbidden -> throw ForbiddenResponse()
            is TenantMembershipControllerService.ReadResult.NotFound -> throw NotFoundResponse("Account not found")
            else -> throw BadRequestResponse()
        }

        val writePrivilege = authentication.privileges.any { it.id == AccountsWritePrivilege.id }
        val writeTenantPrivilege = authentication.privileges.any { it is TenantMembersReadPrivilege }
        val roleAssignPrivilege = authentication.privileges.any { it.id == PlatformRolesAssignPrivilege.id && it !is ITenantPrivilege }

        ctx.renderWithContext(
            "pages/accounts/account.kte",
            "account" to account,
            "activeSession" to if (isSelf) authentication.session else null,
            "isSelf" to isSelf,
            "sessions" to sessions,
            "totpConfiguration" to totpConfiguration,
            "totpQrCodeUri" to totpQrCodeUri,
            "password" to password,
            "memberships" to memberships,
            "allowUpdateEmail" to writePrivilege,
            "allowUpdateProfile" to (writePrivilege || isSelf),
            "allowUpdatePassword" to (writePrivilege || isSelf),
            "allowUpdateTotp" to (writePrivilege || isSelf),
            "allowRevokeSessions" to (writePrivilege || writeTenantPrivilege || isSelf),
            "allowUpdateRoles" to roleAssignPrivilege,
            "allowUpdateMemberships" to writePrivilege,
            "allRoles" to platformRoleService.managedRoles,
        )
    }

    @Get("/account")
    fun self(ctx: Context) {
        val authentication = ctx.requireSession()
        renderAccount(ctx, authentication, null)
    }

    @Get("/accounts/{id}")
    fun identified(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()
        renderAccount(ctx, authentication, id)
    }

    @Post("/account/profile")
    fun updateSelfProfile(ctx: Context) {
        val authentication = ctx.requireSession()

        val email = ctx.formParam("email")?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing email")
        val firstName = ctx.formParam("first_name")?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing first name")
        val lastName = ctx.formParam("last_name")?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing last name")

        when (val result = accountService.updateProfile(authentication, null, email, firstName, lastName)) {
            is AccountControllerService.UpdateProfileResult.Success -> redirectToAccount(ctx, null, "Profile updated")
            is AccountControllerService.UpdateProfileResult.Invalid -> throw BadRequestResponse(result.message)
            is AccountControllerService.UpdateProfileResult.Forbidden -> throw ForbiddenResponse()
            is AccountControllerService.UpdateProfileResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/accounts/{id}/profile")
    fun updateProfile(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()

        val email = ctx.formParam("email")?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing email")
        val firstName = ctx.formParam("first_name")?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing first name")
        val lastName = ctx.formParam("last_name")?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing last name")

        when (val result = accountService.updateProfile(authentication, id, email, firstName, lastName)) {
            is AccountControllerService.UpdateProfileResult.Success -> redirectToAccount(ctx, id, "Profile updated")
            is AccountControllerService.UpdateProfileResult.Invalid -> throw BadRequestResponse(result.message)
            is AccountControllerService.UpdateProfileResult.Forbidden -> throw ForbiddenResponse()
            is AccountControllerService.UpdateProfileResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/account/password")
    fun updateSelfPassword(ctx: Context) {
        val authentication = ctx.requireSession()
        val password = ctx.formParam("password")?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing password")

        when (passwordService.update(authentication, password)) {
            is PasswordControllerService.UpdateResult.Success -> redirectToAccount(ctx, null, "Password updated")
            is PasswordControllerService.UpdateResult.Unauthorized -> throw UnauthorizedResponse()
            is PasswordControllerService.UpdateResult.Forbidden -> throw ForbiddenResponse()
            is PasswordControllerService.UpdateResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/accounts/{id}/password")
    fun updatePassword(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()
        val password = ctx.formParam("password")?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BadRequestResponse("Missing password")

        when (passwordService.update(authentication, password, id)) {
            is PasswordControllerService.UpdateResult.Success -> redirectToAccount(ctx, id, "Password updated")
            is PasswordControllerService.UpdateResult.Unauthorized -> throw UnauthorizedResponse()
            is PasswordControllerService.UpdateResult.Forbidden -> throw ForbiddenResponse()
            is PasswordControllerService.UpdateResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/account/totp/enable")
    fun enableSelfTotp(ctx: Context) {
        val authentication = ctx.requireSession()

        when (totpConfigurationService.enable(authentication)) {
            is TOTPConfigurationControllerService.EnableResult.Success -> redirectToAccount(ctx, null, "TOTP enabled")
            is TOTPConfigurationControllerService.EnableResult.Unauthorized -> throw UnauthorizedResponse()
            is TOTPConfigurationControllerService.EnableResult.Forbidden -> throw ForbiddenResponse()
            is TOTPConfigurationControllerService.EnableResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/accounts/{id}/totp/enable")
    fun enableTotp(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()

        when (totpConfigurationService.enable(authentication, id)) {
            is TOTPConfigurationControllerService.EnableResult.Success -> redirectToAccount(ctx, id, "TOTP enabled")
            is TOTPConfigurationControllerService.EnableResult.Unauthorized -> throw UnauthorizedResponse()
            is TOTPConfigurationControllerService.EnableResult.Forbidden -> throw ForbiddenResponse()
            is TOTPConfigurationControllerService.EnableResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/account/totp/disable")
    fun disableSelfTotp(ctx: Context) {
        val authentication = ctx.requireSession()

        when (totpConfigurationService.disable(authentication)) {
            is TOTPConfigurationControllerService.DisableResult.Success -> redirectToAccount(ctx, null, "TOTP disabled")
            is TOTPConfigurationControllerService.DisableResult.Unauthorized -> throw UnauthorizedResponse()
            is TOTPConfigurationControllerService.DisableResult.Forbidden -> throw ForbiddenResponse()
            is TOTPConfigurationControllerService.DisableResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/accounts/{id}/totp/disable")
    fun disableTotp(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()

        when (totpConfigurationService.disable(authentication, id)) {
            is TOTPConfigurationControllerService.DisableResult.Success -> redirectToAccount(ctx, id, "TOTP disabled")
            is TOTPConfigurationControllerService.DisableResult.Unauthorized -> throw UnauthorizedResponse()
            is TOTPConfigurationControllerService.DisableResult.Forbidden -> throw ForbiddenResponse()
            is TOTPConfigurationControllerService.DisableResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/account/totp/confirm")
    fun confirmSelfTotp(ctx: Context) {
        val authentication = ctx.requireSession()
        val totp = ctx.formParam("totp")?.trim()?.toIntOrNull()
            ?: throw BadRequestResponse("Missing or invalid totp")

        when (totpConfigurationService.confirm(authentication, totp)) {
            is TOTPConfigurationControllerService.ConfirmResult.Success -> redirectToAccount(ctx, null, "TOTP confirmed")
            is TOTPConfigurationControllerService.ConfirmResult.Unauthorized -> throw UnauthorizedResponse()
            is TOTPConfigurationControllerService.ConfirmResult.Forbidden -> throw ForbiddenResponse()
            is TOTPConfigurationControllerService.ConfirmResult.NotFound -> throw NotFoundResponse("Account not found")
            is TOTPConfigurationControllerService.ConfirmResult.Invalid -> throw BadRequestResponse("Invalid TOTP")
        }
    }

    @Post("/accounts/{id}/totp/confirm")
    fun confirmTotp(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()
        val totp = ctx.formParam("totp")?.trim()?.toIntOrNull()
            ?: throw BadRequestResponse("Missing or invalid totp")

        when (totpConfigurationService.confirm(authentication, totp, id)) {
            is TOTPConfigurationControllerService.ConfirmResult.Success -> redirectToAccount(ctx, id, "TOTP confirmed")
            is TOTPConfigurationControllerService.ConfirmResult.Unauthorized -> throw UnauthorizedResponse()
            is TOTPConfigurationControllerService.ConfirmResult.Forbidden -> throw ForbiddenResponse()
            is TOTPConfigurationControllerService.ConfirmResult.NotFound -> throw NotFoundResponse("Account not found")
            is TOTPConfigurationControllerService.ConfirmResult.Invalid -> throw BadRequestResponse("Invalid TOTP")
        }
    }

    @Post("/account/sessions/revoke")
    fun revokeSelfSession(ctx: Context) {
        val authentication = ctx.requireSession()
        val sessionId = parseUuid("session id", ctx.formParam("session_id"))

        when (sessionService.revoke(authentication, sessionId)) {
            is SessionControllerService.RevokeSessionResult.Success -> redirectToAccount(ctx, null, "Session revoked")
            is SessionControllerService.RevokeSessionResult.Unauthorized -> throw UnauthorizedResponse()
            is SessionControllerService.RevokeSessionResult.Forbidden -> throw ForbiddenResponse()
            is SessionControllerService.RevokeSessionResult.NotFound -> throw NotFoundResponse("Session not found")
        }
    }

    @Post("/accounts/{id}/sessions/revoke")
    fun revokeSession(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()
        val sessionId = parseUuid("session id", ctx.formParam("session_id"))

        when (sessionService.revoke(authentication, sessionId, id)) {
            is SessionControllerService.RevokeSessionResult.Success -> redirectToAccount(ctx, id, "Session revoked")
            is SessionControllerService.RevokeSessionResult.Unauthorized -> throw UnauthorizedResponse()
            is SessionControllerService.RevokeSessionResult.Forbidden -> throw ForbiddenResponse()
            is SessionControllerService.RevokeSessionResult.NotFound -> throw NotFoundResponse("Session not found")
        }
    }

    @Post("/account/sessions/revoke-all")
    fun revokeAllSelfSessions(ctx: Context) {
        val authentication = ctx.requireSession()

        when (sessionService.revokeAll(authentication)) {
            is SessionControllerService.RevokeAllSessionsResult.Success -> redirectToAccount(ctx, null, "All sessions revoked")
            is SessionControllerService.RevokeAllSessionsResult.Unauthorized -> throw UnauthorizedResponse()
            is SessionControllerService.RevokeAllSessionsResult.Forbidden -> throw ForbiddenResponse()
            is SessionControllerService.RevokeAllSessionsResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/accounts/{id}/sessions/revoke-all")
    fun revokeAllSessions(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()

        when (sessionService.revokeAll(authentication, id)) {
            is SessionControllerService.RevokeAllSessionsResult.Success -> redirectToAccount(ctx, id, "All sessions revoked")
            is SessionControllerService.RevokeAllSessionsResult.Unauthorized -> throw UnauthorizedResponse()
            is SessionControllerService.RevokeAllSessionsResult.Forbidden -> throw ForbiddenResponse()
            is SessionControllerService.RevokeAllSessionsResult.NotFound -> throw NotFoundResponse("Account not found")
        }
    }

    @Post("/account/roles/assign")
    fun assignSelfRole(ctx: Context) {
        val authentication = ctx.requireSession()
        val roleId = parseRoleId(ctx.formParam("role_id"))

        when (platformRoleService.assign(authentication, roleId)) {
            is PlatformRoleControllerService.AssignResult.Success -> redirectToAccount(ctx, null, "Role assigned")
            is PlatformRoleControllerService.AssignResult.Unauthorized -> throw UnauthorizedResponse()
            is PlatformRoleControllerService.AssignResult.Forbidden -> throw ForbiddenResponse()
            is PlatformRoleControllerService.AssignResult.AccountNotFound -> throw NotFoundResponse("Account not found")
            is PlatformRoleControllerService.AssignResult.RoleNotFound -> throw NotFoundResponse("Role not found")
        }
    }

    @Post("/accounts/{id}/roles/assign")
    fun assignRole(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()
        val roleId = parseRoleId(ctx.formParam("role_id"))

        when (platformRoleService.assign(authentication, roleId, id)) {
            is PlatformRoleControllerService.AssignResult.Success -> redirectToAccount(ctx, id, "Role assigned")
            is PlatformRoleControllerService.AssignResult.Unauthorized -> throw UnauthorizedResponse()
            is PlatformRoleControllerService.AssignResult.Forbidden -> throw ForbiddenResponse()
            is PlatformRoleControllerService.AssignResult.AccountNotFound -> throw NotFoundResponse("Account not found")
            is PlatformRoleControllerService.AssignResult.RoleNotFound -> throw NotFoundResponse("Role not found")
        }
    }

    @Post("/account/roles/unassign")
    fun unassignSelfRole(ctx: Context) {
        val authentication = ctx.requireSession()
        val roleId = parseRoleId(ctx.formParam("role_id"))

        when (platformRoleService.unassign(authentication, roleId)) {
            is PlatformRoleControllerService.UnassignResult.Success -> redirectToAccount(ctx, null, "Role removed")
            is PlatformRoleControllerService.UnassignResult.Unauthorized -> throw UnauthorizedResponse()
            is PlatformRoleControllerService.UnassignResult.Forbidden -> throw ForbiddenResponse()
            is PlatformRoleControllerService.UnassignResult.AccountNotFound -> throw NotFoundResponse("Account not found")
            is PlatformRoleControllerService.UnassignResult.RoleNotFound -> throw NotFoundResponse("Role not found")
            is PlatformRoleControllerService.UnassignResult.NotFound -> throw NotFoundResponse("Role not assigned")
        }
    }

    @Post("/accounts/{id}/roles/unassign")
    fun unassignRole(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()
        val roleId = parseRoleId(ctx.formParam("role_id"))

        when (platformRoleService.unassign(authentication, roleId, id)) {
            is PlatformRoleControllerService.UnassignResult.Success -> redirectToAccount(ctx, id, "Role removed")
            is PlatformRoleControllerService.UnassignResult.Unauthorized -> throw UnauthorizedResponse()
            is PlatformRoleControllerService.UnassignResult.Forbidden -> throw ForbiddenResponse()
            is PlatformRoleControllerService.UnassignResult.AccountNotFound -> throw NotFoundResponse("Account not found")
            is PlatformRoleControllerService.UnassignResult.RoleNotFound -> throw NotFoundResponse("Role not found")
            is PlatformRoleControllerService.UnassignResult.NotFound -> throw NotFoundResponse("Role not assigned")
        }
    }

    @Post("/account/memberships/assign")
    fun assignSelfMembership(ctx: Context) {
        val authentication = ctx.requireSession()
        val tenantId = parseUuid("tenant id", ctx.formParam("tenant_id"))

        when (tenantMembershipService.assign(authentication, tenantId)) {
            is TenantMembershipControllerService.AssignResult.Success -> redirectToAccount(ctx, null, "Membership assigned")
            is TenantMembershipControllerService.AssignResult.Unauthorized -> throw UnauthorizedResponse()
            is TenantMembershipControllerService.AssignResult.Forbidden -> throw ForbiddenResponse()
            is TenantMembershipControllerService.AssignResult.AccountNotFound -> throw NotFoundResponse("Account not found")
            is TenantMembershipControllerService.AssignResult.TenantNotFound -> throw NotFoundResponse("Tenant not found")
        }
    }

    @Post("/accounts/{id}/memberships/assign")
    fun assignMembership(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()
        val tenantId = parseUuid("tenant id", ctx.formParam("tenant_id"))

        when (tenantMembershipService.assign(authentication, tenantId, id)) {
            is TenantMembershipControllerService.AssignResult.Success -> redirectToAccount(ctx, id, "Membership assigned")
            is TenantMembershipControllerService.AssignResult.Unauthorized -> throw UnauthorizedResponse()
            is TenantMembershipControllerService.AssignResult.Forbidden -> throw ForbiddenResponse()
            is TenantMembershipControllerService.AssignResult.AccountNotFound -> throw NotFoundResponse("Account not found")
            is TenantMembershipControllerService.AssignResult.TenantNotFound -> throw NotFoundResponse("Tenant not found")
        }
    }

    @Post("/account/memberships/unassign")
    fun unassignSelfMembership(ctx: Context) {
        val authentication = ctx.requireSession()
        val tenantId = parseUuid("tenant id", ctx.formParam("tenant_id"))

        when (tenantMembershipService.unassign(authentication, tenantId)) {
            is TenantMembershipControllerService.UnassignResult.Success -> redirectToAccount(ctx, null, "Membership removed")
            is TenantMembershipControllerService.UnassignResult.Unauthorized -> throw UnauthorizedResponse()
            is TenantMembershipControllerService.UnassignResult.Forbidden -> throw ForbiddenResponse()
            is TenantMembershipControllerService.UnassignResult.NotFound -> throw NotFoundResponse("Membership not found")
        }
    }

    @Post("/accounts/{id}/memberships/unassign")
    fun unassignMembership(ctx: Context, @Param id: UUID) {
        val authentication = ctx.requireSession()
        val tenantId = parseUuid("tenant id", ctx.formParam("tenant_id"))

        when (tenantMembershipService.unassign(authentication, tenantId, id)) {
            is TenantMembershipControllerService.UnassignResult.Success -> redirectToAccount(ctx, id, "Membership removed")
            is TenantMembershipControllerService.UnassignResult.Unauthorized -> throw UnauthorizedResponse()
            is TenantMembershipControllerService.UnassignResult.Forbidden -> throw ForbiddenResponse()
            is TenantMembershipControllerService.UnassignResult.NotFound -> throw NotFoundResponse("Membership not found")
        }
    }
}
