package com.jasminesoftwaresolutions.id.domain.registries

import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStep

interface IAuthenticationStepRegistry {
    class Entry<T : IAuthenticationFlow>(
        val step: AuthenticationFlowStep,
        val afterAnyOf: Set<AuthenticationFlowStep> = emptySet(),
        val condition: (T) -> Boolean = { true },
        val priority: Int = 0
    ) {
        constructor(
            step: AuthenticationFlowStep,
            after: AuthenticationFlowStep?,
            condition: (IAuthenticationFlow) -> Boolean = { true }
        ) : this(step, after?.let(::setOf) ?: emptySet(), condition)
    }

    fun entries(): Set<Entry<IAuthenticationFlow>>

    fun register(
        step: AuthenticationFlowStep,
        afterAnyOf: Set<AuthenticationFlowStep> = emptySet(),
        condition: (IAuthenticationFlow) -> Boolean = { true },
        priority: Int = 0,
        force: Boolean = false
    )

    fun register(
        step: AuthenticationFlowStep,
        after: AuthenticationFlowStep? = null,
        condition: (IAuthenticationFlow) -> Boolean = { true },
        priority: Int = 0,
        force: Boolean = false
    ) = register(step, after?.let(::setOf) ?: emptySet(), condition, priority, force)

    fun findByFqdn(fqdn: String): Entry<IAuthenticationFlow>?

    fun nextOrNull(flow: IAuthenticationFlow): AuthenticationFlowStep?
        = entries().filter {
            val currentStepFqdn = flow.steps.currentOrNull()?.fqdn
            val isMatchingDependency = if (currentStepFqdn == null) {
                it.afterAnyOf.isEmpty()
            } else {
                it.afterAnyOf.any { dependency -> dependency.fqdn == currentStepFqdn }
            }

            isMatchingDependency && it.condition(flow)
        }.maxByOrNull { it.priority }?.step
}

open class AuthenticationStepRegistry : IAuthenticationStepRegistry {
    protected val steps: MutableSet<IAuthenticationStepRegistry.Entry<IAuthenticationFlow>> = mutableSetOf()

    override fun entries(): Set<IAuthenticationStepRegistry.Entry<IAuthenticationFlow>>
        = HashSet(steps)

    override fun register(
        step: AuthenticationFlowStep,
        afterAnyOf: Set<AuthenticationFlowStep>,
        condition: (IAuthenticationFlow) -> Boolean,
        priority: Int,
        force: Boolean
    ) {
        val existingStep = findByFqdn(step.fqdn)
        if (existingStep != null && !force) return

        steps.removeIf { it.step.fqdn == step.fqdn }
        steps.add(IAuthenticationStepRegistry.Entry(step, afterAnyOf, condition, priority))
    }

    override fun findByFqdn(fqdn: String): IAuthenticationStepRegistry.Entry<IAuthenticationFlow>?
        = steps.find { it.step.fqdn == fqdn }
}
