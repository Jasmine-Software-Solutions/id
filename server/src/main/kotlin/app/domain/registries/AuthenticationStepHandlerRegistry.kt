package app.domain.registries

import app.domain.services.authentication.AuthenticationFlowStep
import app.domain.services.authentication.AuthenticationFlowStepHandler

interface IAuthenticationStepHandlerRegistry {
    fun entries(): Map<AuthenticationFlowStep, AuthenticationFlowStepHandler<*, *>>

    fun <Request, Response, T : AuthenticationFlowStepHandler<out Request, out Response>> register(step: AuthenticationFlowStep, handler: T, force: Boolean = false)

    operator fun get(step: AuthenticationFlowStep): AuthenticationFlowStepHandler<*, *>?
        = entries().entries.firstOrNull { it.key.fqdn == step.fqdn }?.value
}

open class AuthenticationStepHandlerRegistry(
    protected val stepRegistry: IAuthenticationStepRegistry
) : IAuthenticationStepHandlerRegistry {
    protected val handlers = mutableMapOf<AuthenticationFlowStep, AuthenticationFlowStepHandler<*, *>>()

    override fun entries(): Map<AuthenticationFlowStep, AuthenticationFlowStepHandler<*, *>>
        = handlers.toMap()

    override fun <Request, Response, T : AuthenticationFlowStepHandler<out Request, out Response>> register(
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