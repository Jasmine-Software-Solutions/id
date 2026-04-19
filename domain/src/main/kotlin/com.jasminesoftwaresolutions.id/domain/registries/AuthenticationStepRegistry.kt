package com.jasminesoftwaresolutions.id.domain.registries

import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStep

interface IAuthenticationStepRegistry<T : IAuthenticationFlow> {
    class Entry<T : IAuthenticationFlow>(
        val step: AuthenticationFlowStep,
        val after: AuthenticationFlowStep? = null,
        val condition: (T) -> Boolean = { true }
    )

    fun entries(): Set<Entry<T>>

    fun register(
        step: AuthenticationFlowStep,
        after: AuthenticationFlowStep? = null,
        condition: (T) -> Boolean = { true },
        force: Boolean = false
    )

    fun findByFqdn(fqdn: String): Entry<T>?

    fun nextOrNull(flow: T): AuthenticationFlowStep?
        = entries().firstOrNull { it.after?.fqdn == flow.steps.currentOrNull()?.fqdn && it.condition(flow) }?.step
}

open class AuthenticationStepRegistry<T : IAuthenticationFlow> :
    IAuthenticationStepRegistry<T> {
    protected val steps: MutableSet<IAuthenticationStepRegistry.Entry<T>> = mutableSetOf()

    override fun entries(): Set<IAuthenticationStepRegistry.Entry<T>>
        = HashSet(steps)

    override fun register(
        step: AuthenticationFlowStep,
        after: AuthenticationFlowStep?,
        condition: (T) -> Boolean,
        force: Boolean
    ) {
        val existingStep = findByFqdn(step.fqdn)
        if (existingStep != null && !force) return

        steps.removeIf { it.step.fqdn == step.fqdn }
        steps.add(IAuthenticationStepRegistry.Entry(step, after, condition))
    }

    override fun findByFqdn(fqdn: String): IAuthenticationStepRegistry.Entry<T>?
        = steps.find { it.step.fqdn == fqdn }
}