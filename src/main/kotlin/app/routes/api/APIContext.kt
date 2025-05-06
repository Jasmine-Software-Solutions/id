package app.routes.api

import app.routes.oauth2.OAuth2AuthorizationRoute.requireAuthorization
import app.sql.audit.ClientAuditTable
import app.sql.audit.SessionAuditTable
import app.sql.oauth2.MachineAccessTokens
import app.sql.oauth2.SessionAccessTokens
import app.sql.tenant.TenantAccountLinksTable
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.UnauthorizedResponse
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

val Context.tenantId: UUID?
    get() {
        val specifiedTenant = queryParam("tenant")?.let { UUID.fromString(it) }
        if (specifiedTenant != null)
            return specifiedTenant

        val tokens = requireAuthorization()
        if (tokens is SessionAccessTokens)
            return tokens.tenant.id.value

        if (tokens is MachineAccessTokens)
            return tokens.tenant.id.value

        return null
    }

fun Context.requireAdministratorAndScope(scope: String) {
    val tokens = requireAuthorization()

    if (!tokens.authorizedFor(scope, tenantId))
        throw ForbiddenResponse()

    // Check resource owner's permissions
    transaction {
        // MachineAccessTokens are permitted to access all resources within scope
        // because they are not bound to a Resource Owner's permissions, but instead
        // to a Client's permissions.
        if (tokens is MachineAccessTokens)
            return@transaction

        if (tokens !is SessionAccessTokens)
            throw UnauthorizedResponse()

        val account = tokens.session.account

        var linkedAsAdministrator = false
        if (tenantId != null)
            linkedAsAdministrator = TenantAccountLinksTable.select {
                TenantAccountLinksTable.account eq account.id and
                        (TenantAccountLinksTable.tenant eq tenantId)
            }.firstOrNull()?.get(TenantAccountLinksTable.administrator) ?: false

        if (!linkedAsAdministrator && !account.systemAdmin)
            throw ForbiddenResponse()
    }
}

fun Context.writeApiAudit(detail: String) {
    val tokens = requireAuthorization()

    transaction {
        when (tokens) {
            is SessionAccessTokens -> SessionAuditTable.write(this@writeApiAudit, detail)
            is MachineAccessTokens -> ClientAuditTable.write(this@writeApiAudit, detail)
        }
    }
}