package app.domain.registries

import app.domain.services.authentication.AuthenticationFlowStep
import app.domain.services.authentication.IAuthenticationFlowStepHandler

interface IAuthenticationStepHandlerRegistry {
    fun entries(): Map<AuthenticationFlowStep, IAuthenticationFlowStepHandler<*, *>>

    fun <Request, Response, T : IAuthenticationFlowStepHandler<out Request, out Response>> register(step: AuthenticationFlowStep, handler: T, force: Boolean = false)

    operator fun get(step: AuthenticationFlowStep): IAuthenticationFlowStepHandler<*, *>?
        = entries().entries.firstOrNull { it.key.fqdn == step.fqdn }?.value
}

open class AuthenticationStepHandlerRegistry(
    protected val stepRegistry: IAuthenticationStepRegistry
) : IAuthenticationStepHandlerRegistry {
    protected val handlers = mutableMapOf<AuthenticationFlowStep, IAuthenticationFlowStepHandler<*, *>>()

    override fun entries(): Map<AuthenticationFlowStep, IAuthenticationFlowStepHandler<*, *>>
        = handlers.toMap()

    override fun <Request, Response, T : IAuthenticationFlowStepHandler<out Request, out Response>> register(
        step: AuthenticationFlowStep,
        handler: T,
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