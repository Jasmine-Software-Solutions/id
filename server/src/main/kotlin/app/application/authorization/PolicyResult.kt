package app.application.authorization

sealed class PolicyResult<AccessToken> {
    class Pass<AccessToken>(private val policies: List<Policy<AccessToken>>, val token: AccessToken) : PolicyResult<AccessToken>() {
        constructor(policy: Policy<AccessToken>, token: AccessToken) : this(listOf(policy), token)

        fun policies(): List<Policy<AccessToken>> {
            fun flatten(policy: Policy<AccessToken>): List<Policy<AccessToken>> = when (policy) {
                is Policy.And -> policy.policies.flatMap { flatten(it) }
                is Policy.Or -> policy.policies.flatMap { flatten(it) }
                else -> listOf(policy)
            }

            return policies.flatMap { flatten(it) }
        }
    }

    class Fail<AccessToken> : PolicyResult<AccessToken>()
}