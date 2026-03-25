package app.application.authorization

import app.application.oauth2.principal
import app.infrastructure.models.oauth2.AccessToken
import app.infrastructure.models.oauth2.MachineAccessToken
import app.infrastructure.models.oauth2.SessionAccessToken
import app.infrastructure.models.tenant.TenantAccountLinksTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.util.*

/**
 * Policy that can be evaluated against an access token.
 */
interface Policy<AccessToken> {
    fun evaluate(token: AccessToken): PolicyResult<AccessToken>

    /**
     * Checks if the token has the specified scope, optionally for a specific tenant.
     */
    data class HasScope(val tenant: UUID? = null, val scope: String) : Policy<AccessToken> {
        override fun evaluate(token: AccessToken): PolicyResult<AccessToken> =
            if (token.principal().authorizedFor(scope, tenant)) PolicyResult.Pass(this, token)
            else PolicyResult.Fail()
    }

    /**
     * Checks if the token represents an administrator.
     * For machine tokens, this always returns true.
     * For session tokens, checks if the account is a system admin or linked as administrator for the tenant.
     */
    data class IsAdministrator(val tenant: UUID? = null) : Policy<AccessToken> {
        override fun evaluate(token: AccessToken): PolicyResult<AccessToken> {
            if (token is MachineAccessToken) {
                if (tenant == null || token.tenant.id.value == tenant)
                    return PolicyResult.Pass(this, token)
                return PolicyResult.Fail()
            }
            if (token !is SessionAccessToken) return PolicyResult.Fail()

            val account = token.session.account
            val linkedAsAdministrator = if (tenant != null) {
                TenantAccountLinksTable.select {
                    TenantAccountLinksTable.account eq account.id and
                            (TenantAccountLinksTable.tenant eq tenant)
                }.firstOrNull()?.get(TenantAccountLinksTable.administrator) ?: false
            } else false

            return if (linkedAsAdministrator || account.systemAdmin) PolicyResult.Pass(this, token)
            else PolicyResult.Fail()
        }
    }

    /**
     * Checks if the access token is active.
     */
    object IsTokenActive : Policy<AccessToken> {
        override fun evaluate(token: AccessToken): PolicyResult<AccessToken> =
            if (token.principal().isAccessTokenActive()) PolicyResult.Pass(this, token)
            else PolicyResult.Fail()
    }

    object Allow : Policy<Any> {
        override fun evaluate(token: Any): PolicyResult<Any> = PolicyResult.Pass(this, token)
    }

    object Deny : Policy<Any> {
        override fun evaluate(token: Any): PolicyResult<Any> = PolicyResult.Fail()
    }

    /**
     * Combines multiple policies with AND logic.
     */
    data class And<AccessToken>(val policies: List<Policy<AccessToken>>) : Policy<AccessToken> {
        override fun evaluate(token: AccessToken): PolicyResult<AccessToken> {
            val results = policies.map { it.evaluate(token) }
            return if (results.all { it is PolicyResult.Pass }) PolicyResult.Pass(policies.toList(), token)
            else PolicyResult.Fail()
        }
    }

    /**
     * Combines multiple policies with OR logic.
     */
    data class Or<AccessToken>(val policies: List<Policy<AccessToken>>) : Policy<AccessToken> {
        override fun evaluate(token: AccessToken): PolicyResult<AccessToken> {
            val results = policies.associateWith { it.evaluate(token) }
                .filter { it.value is PolicyResult.Pass }

            if (results.isEmpty()) return PolicyResult.Fail()
            return PolicyResult.Pass(results.map { it.key }.toList(), token)
        }
    }
}
