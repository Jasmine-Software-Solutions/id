package app.application.oauth2

import app.infrastructure.models.oauth2.MachineAccessToken
import java.time.Instant
import java.util.*

object MachineTokenAuthorizationService {
    fun isAccessTokenActive(token: MachineAccessToken) =
        token.issuedAt.isBefore(Instant.now()) && Instant.now().isBefore(token.expiresAt)

    fun authorizedFor(token: MachineAccessToken, scope: String, tenant: UUID?): Boolean {
        if (tenant != null && token.tenant.id.value != tenant)
            return false

        val scopes = token.scope?.split(" ") ?: return true
        val hasScope = scopes.contains(scope)
        return hasScope
    }
}
