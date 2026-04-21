package com.jasminesoftwaresolutions.id.domain.services.authorization

/**
 * Policy that can be evaluated against an access request.
 */
interface Policy<TContext> {
    fun evaluate(context: TContext): PolicyResult<TContext>

    object Allow : Policy<Any> {
        override fun evaluate(context: Any): PolicyResult<Any> = PolicyResult.Pass(this, context)
    }

    object Deny : Policy<Any> {
        override fun evaluate(context: Any): PolicyResult<Any> = PolicyResult.Fail()
    }

    /**
     * Combines multiple policies with AND logic.
     */
    data class And<TContext>(val policies: List<Policy<TContext>>) : Policy<TContext> {
        override fun evaluate(context: TContext): PolicyResult<TContext> {
            val results = policies.map { it.evaluate(context) }
            return if (results.all { it is PolicyResult.Pass }) PolicyResult.Pass(policies.toList(), context)
            else PolicyResult.Fail()
        }
    }

    /**
     * Combines multiple policies with OR logic.
     */
    data class Or<TContext>(val policies: List<Policy<TContext>>) : Policy<TContext> {
        override fun evaluate(context: TContext): PolicyResult<TContext> {
            val results = policies.associateWith { it.evaluate(context) }
                .filter { it.value is PolicyResult.Pass }

            if (results.isEmpty()) return PolicyResult.Fail()
            return PolicyResult.Pass(results.map { it.key }.toList(), context)
        }
    }
}