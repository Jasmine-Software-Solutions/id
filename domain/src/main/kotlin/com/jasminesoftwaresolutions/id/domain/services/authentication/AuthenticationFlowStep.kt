package com.jasminesoftwaresolutions.id.domain.services.authentication

import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import kotlin.reflect.KClass

open class AuthenticationFlowStep(
    val fqdn: String,
    val level: Int = 0
) {
    open fun alternatives(): Set<AuthenticationFlowStep> = setOf()
}

abstract class AuthenticationFlowStepHandler<TFlow : IAuthenticationFlow, TRequest : Any, TResponse : Any>(
    val requestClass: KClass<TRequest>,
    val responseClass: KClass<TResponse>
) {
    abstract fun create(flow: TFlow): TRequest
    abstract fun accept(flow: TFlow, request: TRequest, response: TResponse): AuthenticationFlowStepResult
}