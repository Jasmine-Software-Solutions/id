package com.jasminesoftwaresolutions.id.domain.services.authorization

sealed class PolicyResult<TContext> {
    class Pass<TContext>(
        private val policies: List<Policy<TContext>>,
        val context: TContext
    ) : PolicyResult<TContext>() {

        constructor(
            policy: Policy<TContext>,
            context: TContext,
        ) : this(listOf(policy), context)

        fun policies(): List<Policy<TContext>> {
            fun flatten(policy: Policy<TContext>): List<Policy<TContext>> = when (policy) {
                is Policy.And -> policy.policies.flatMap { flatten(it) }
                is Policy.Or -> policy.policies.flatMap { flatten(it) }
                else -> listOf(policy)
            }

            return policies.flatMap { flatten(it) }
        }
    }

    class Fail<TContext> : PolicyResult<TContext>()
}