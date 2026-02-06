package app.application.authorization

interface IAuthorizationEngine<AccessToken> {
    fun evaluate(token: AccessToken, policy: Policy<AccessToken>): PolicyResult<AccessToken>
        = policy.evaluate(token)

    fun evaluateAll(token: AccessToken, vararg policies: Policy<AccessToken>): PolicyResult<AccessToken>
        = evaluate(token, Policy.And(policies.toList()))

    fun evaluateAny(token: AccessToken, vararg policies: Policy<AccessToken>): PolicyResult<AccessToken>
        = evaluate(token, Policy.Or(policies.toList()))
}