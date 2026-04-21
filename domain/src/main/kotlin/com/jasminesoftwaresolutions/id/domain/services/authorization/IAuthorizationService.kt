package com.jasminesoftwaresolutions.id.domain.services.authorization

interface IAuthorizationService<TRequest, TContext> {
    fun authenticate(request: TRequest): TContext?

    fun evaluate(request: TRequest, policy: Policy<TContext>): PolicyResult<TContext>
        = policy.evaluate(authenticate(request) ?: return PolicyResult.Fail())

    fun evaluateAll(request: TRequest, vararg policies: Policy<TContext>): PolicyResult<TContext>
        = evaluate(request, Policy.And(policies.toList()))

    fun evaluateAny(request: TRequest, vararg policies: Policy<TContext>): PolicyResult<TContext>
        = evaluate(request, Policy.Or(policies.toList()))
}