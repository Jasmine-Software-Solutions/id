package com.jasminesoftwaresolutions.id.domain.registries

import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStepHandler

interface IAuthenticationStepHandlerRegistry<T : IAuthenticationFlow> {
    fun entries(): Map<AuthenticationFlowStep, AuthenticationFlowStepHandler<T, *, *>>

    fun <TRequest : Any, TResponse : Any, THandler : AuthenticationFlowStepHandler<T, out TRequest, out TResponse>> register(step: AuthenticationFlowStep, handler: THandler, force: Boolean = false)

    operator fun get(step: AuthenticationFlowStep): AuthenticationFlowStepHandler<T, *, *>?
        = entries().entries.firstOrNull { it.key.fqdn == step.fqdn }?.value
}

open class AuthenticationStepHandlerRegistry<T : IAuthenticationFlow>() :
    IAuthenticationStepHandlerRegistry<T> {
    protected val handlers = mutableMapOf<AuthenticationFlowStep, AuthenticationFlowStepHandler<T, *, *>>()

    override fun entries(): Map<AuthenticationFlowStep, AuthenticationFlowStepHandler<T, *, *>>
        = handlers.toMap()

    override fun <TRequest : Any, TResponse : Any, THandler : AuthenticationFlowStepHandler<T, out TRequest, out TResponse>> register(
        step: AuthenticationFlowStep,
        handler: THandler,
        force: Boolean
    ) {
        val existingStep = entries().keys.firstOrNull { it.fqdn == step.fqdn }

        if (existingStep != null && !force) return
        else if (existingStep != null)
            handlers.remove(existingStep)

        handlers[step] = handler
    }
}