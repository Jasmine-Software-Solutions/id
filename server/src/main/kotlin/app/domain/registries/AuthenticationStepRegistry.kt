package app.domain.registries

import app.domain.models.authentication.IAuthenticationFlow
import app.domain.services.authentication.AuthenticationFlowStep

interface IAuthenticationStepRegistry {
    class Entry(
        val step: AuthenticationFlowStep,
        val after: AuthenticationFlowStep? = null,
        val condition: (IAuthenticationFlow) -> Boolean = { true }
    )

    fun entries(): Set<Entry>

    fun register(
        step: AuthenticationFlowStep,
        after: AuthenticationFlowStep? = null,
        condition: (IAuthenticationFlow) -> Boolean = { true },
        force: Boolean = false
    )

    fun findByFqdn(fqdn: String): Entry?

    fun firstOrNull(flow: IAuthenticationFlow): AuthenticationFlowStep?
        = entries().firstOrNull { it.after == null && it.condition(flow) }?.step

    fun nextOrNull(flow: IAuthenticationFlow): AuthenticationFlowStep?
        = entries().firstOrNull { it.after?.fqdn == flow.steps.values.last().fqdn && it.condition(flow) }?.step ?: firstOrNull(flow)
}

open class AuthenticationStepRegistry : IAuthenticationStepRegistry {
    protected val steps: MutableSet<IAuthenticationStepRegistry.Entry> = mutableSetOf()

    override fun entries(): Set<IAuthenticationStepRegistry.Entry>
        = HashSet(steps)

    override fun register(
        step: AuthenticationFlowStep,
        after: AuthenticationFlowStep?,
        condition: (IAuthenticationFlow) -> Boolean,
        force: Boolean
    ) {
        val existingStep = findByFqdn(step.fqdn)
        if (existingStep != null && !force) return

        steps.removeIf { it.step.fqdn == step.fqdn }
        steps.add(IAuthenticationStepRegistry.Entry(step, after, condition))
    }

    override fun findByFqdn(fqdn: String): IAuthenticationStepRegistry.Entry?
        = steps.find { it.step.fqdn == fqdn }
}