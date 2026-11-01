package com.jasminesoftwaresolutions.id.domain.registries

import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStepHandler

interface IAuthenticationStepHandlerRegistry {
    fun entries(): Map<AuthenticationFlowStep, AuthenticationFlowStepHandler<*, *>>

    fun <TRequest : Any, TResponse : Any, THandler : AuthenticationFlowStepHandler<out TRequest, out TResponse>> register(step: AuthenticationFlowStep, handler: THandler, force: Boolean = false)

    operator fun get(step: AuthenticationFlowStep): AuthenticationFlowStepHandler<*, *>?
        = entries().entries.firstOrNull { it.key.fqdn == step.fqdn }?.value
}

open class AuthenticationStepHandlerRegistry() : IAuthenticationStepHandlerRegistry {
    protected val handlers = mutableMapOf<AuthenticationFlowStep, AuthenticationFlowStepHandler<*, *>>()

    override fun entries(): Map<AuthenticationFlowStep, AuthenticationFlowStepHandler<*, *>>
        = handlers.toMap()

    override fun <TRequest : Any, TResponse : Any, THandler : AuthenticationFlowStepHandler<out TRequest, out TResponse>> register(
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