package com.jasminesoftwaresolutions.id.domain.services.authorization

import com.jasminesoftwaresolutions.id.domain.models.client.IDelegatedSession

/**
 * Resolves OIDC standard claims for an authenticated user based on the scopes granted.
 */
interface IOIDCClaimsResolver {
    /**
     * Resolve claims for the given [account] based on the scopes in [delegatedSession].
     * Returns a map of claim name to value suitable for inclusion in an ID Token
     * or UserInfo response.
     */
    fun resolveClaims(
        delegatedSession: IDelegatedSession,
    ): Map<String, Any?>
}

class StandardOIDCClaimsResolver : IOIDCClaimsResolver {
    override fun resolveClaims(delegatedSession: IDelegatedSession): Map<String, Any?> {
        val account = delegatedSession.session.account
        val scopeIds = delegatedSession.scopes.map { it.id }.toSet()
        val claims = mutableMapOf<String, Any?>()

        // sub is always included when openid scope is present
        claims["sub"] = account.id.toString()

        if ("profile" in scopeIds) {
            claims["name"] = "${account.firstName} ${account.lastName}".trim()
            claims["given_name"] = account.firstName
            claims["family_name"] = account.lastName
        }

        if ("email" in scopeIds) {
            claims["email"] = account.email
            claims["email_verified"] = account.emailVerified
        }

        return claims
    }
}