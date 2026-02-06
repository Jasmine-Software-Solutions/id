package app.application.authorization

import app.infrastructure.models.oauth2.*
import org.jetbrains.exposed.sql.transactions.transaction

object APIAuthorizationEngine : IAuthorizationEngine<AccessToken> {
    /**
     * Evaluates a policy against an access token.
     *
     * @param token The access token to evaluate
     * @param policy The policy to evaluate
     * @return PolicyResult containing the token and whether the policy passed
     */
    override fun evaluate(token: AccessToken, policy: Policy<AccessToken>): PolicyResult<AccessToken> = transaction {
        policy.evaluate(token)
    }

    /**
     * Evaluates a policy against an authorization header.
     *
     * @param authorizationHeader The authorization header containing the Bearer token
     * @param policy The policy to evaluate
     * @return PolicyResult containing the token (if found) and whether the policy passed
     */
    fun evaluate(authorizationHeader: String?, policy: Policy<AccessToken>): PolicyResult<AccessToken> = transaction {
        val token = getAccessTokenFromAuthorizationHeader(authorizationHeader)
            ?: return@transaction PolicyResult.Fail()
        policy.evaluate(token)
    }

    /**
     * Evaluates multiple policies with AND logic.
     */
    fun evaluateAll(authorizationHeader: String?, vararg policies: Policy<AccessToken>): PolicyResult<AccessToken> =
        evaluate(authorizationHeader, Policy.And(policies.toList()))

    /**
     * Evaluates multiple policies with OR logic.
     */
    fun evaluateAny(authorizationHeader: String?, vararg policies: Policy<AccessToken>): PolicyResult<AccessToken> =
        evaluate(authorizationHeader, Policy.Or(policies.toList()))

    /**
     * Reads and validates an access token from an authorization header.
     */
    private fun getAccessTokenFromAuthorizationHeader(authorizationHeader: String?): AccessToken? = transaction {
        val authorization = authorizationHeader ?: return@transaction null
        if (!authorization.startsWith("Bearer ", ignoreCase = true))
            return@transaction null

        val token = authorization.substringAfter("Bearer ").trim()
        if (token.isEmpty()) return@transaction null

        val sessionTokens = SessionAccessToken.find {
            SessionAccessTokensTable.accessToken eq token
        }.firstOrNull()
        val machineTokens = MachineAccessToken.find {
            MachineAccessTokensTable.accessToken eq token
        }.firstOrNull()

        val tokens = sessionTokens ?: machineTokens
        ?: return@transaction null

        tokens
    }
}