package com.jasminesoftwaresolutions.id.domain.services.authentication

interface IRequestAuthenticationService<TRequest, TContext> {
    fun authenticate(request: TRequest): TContext?
}