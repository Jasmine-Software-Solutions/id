package app.domain.registries

import app.domain.models.authentication.IAuthenticationFlow
import app.domain.services.authentication.AuthenticationFlowStep
import app.domain.services.authentication.AuthenticationFlowStepHandler

interface IAuthenticationStepHandlerRegistry<T : IAuthenticationFlow> {
    fun entries(): Map<AuthenticationFlowStep, AuthenticationFlowStepHandler<T, *, *>>

    fun <TRequest : Any, TResponse : Any, THandler : AuthenticationFlowStepHandler<T, out TRequest, out TResponse>> register(step: AuthenticationFlowStep, handler: THandler, force: Boolean = false)

    operator fun get(step: AuthenticationFlowStep): AuthenticationFlowStepHandler<T, *, *>?
        = entries().entries.firstOrNull { it.key.fqdn == step.fqdn }?.value
}

open class AuthenticationStepHandlerRegistry<T : IAuthenticationFlow>(
    protected val stepRegistry: IAuthenticationStepRegistry<T>
) : IAuthenticationStepHandlerRegistry<T> {
    protected val handlers = mutableMapOf<AuthenticationFlowStep, AuthenticationFlowStepHandler<T, *, *>>()

    override fun entries(): Map<AuthenticationFlowStep, AuthenticationFlowStepHandler<T, *, *>>
        = handlers.toMap()

    override fun <TRequest : Any, TResponse : Any, THandler : AuthenticationFlowStepHandler<T, out TRequest, out TResponse>> register(
        step: AuthenticationFlowStep,
        handler: THandler,
        force: Boolean
    ) {
        val existingStep = stepRegistry.findByFqdn(step.fqdn)?.step
            ?: throw IllegalArgumentException(step.fqdn + " has not been registered.")

        val existingHandler = handlers[existingStep]
        if (existingHandler != null && !force) return

        handlers.remove(existingStep)
        handlers[existingStep] = handler
    }
}