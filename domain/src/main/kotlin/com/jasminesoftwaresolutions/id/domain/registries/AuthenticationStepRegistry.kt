package com.jasminesoftwaresolutions.id.domain.registries

import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStep

interface IAuthenticationStepRegistry<T : IAuthenticationFlow> {
    class Entry<T : IAuthenticationFlow>(
        val step: AuthenticationFlowStep,
        val afterAnyOf: Set<AuthenticationFlowStep> = emptySet(),
        val condition: (T) -> Boolean = { true },
        val priority: Int = 0
    ) {
        constructor(
            step: AuthenticationFlowStep,
            after: AuthenticationFlowStep?,
            condition: (T) -> Boolean = { true }
        ) : this(step, after?.let(::setOf) ?: emptySet(), condition)
    }

    fun entries(): Set<Entry<T>>

    fun register(
        step: AuthenticationFlowStep,
        afterAnyOf: Set<AuthenticationFlowStep> = emptySet(),
        condition: (T) -> Boolean = { true },
        priority: Int = 0,
        force: Boolean = false
    )

    fun register(
        step: AuthenticationFlowStep,
        after: AuthenticationFlowStep? = null,
        condition: (T) -> Boolean = { true },
        priority: Int = 0,
        force: Boolean = false
    ) = register(step, after?.let(::setOf) ?: emptySet(), condition, priority, force)

    fun findByFqdn(fqdn: String): Entry<T>?

    fun nextOrNull(flow: T): AuthenticationFlowStep?
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

open class AuthenticationStepRegistry<T : IAuthenticationFlow> :
    IAuthenticationStepRegistry<T> {
    protected val steps: MutableSet<IAuthenticationStepRegistry.Entry<T>> = mutableSetOf()

    override fun entries(): Set<IAuthenticationStepRegistry.Entry<T>>
        = HashSet(steps)

    override fun register(
        step: AuthenticationFlowStep,
        afterAnyOf: Set<AuthenticationFlowStep>,
        condition: (T) -> Boolean,
        priority: Int,
        force: Boolean
    ) {
        val existingStep = findByFqdn(step.fqdn)
        if (existingStep != null && !force) return

        steps.removeIf { it.step.fqdn == step.fqdn }
        steps.add(IAuthenticationStepRegistry.Entry(step, afterAnyOf, condition, priority))
    }

    override fun findByFqdn(fqdn: String): IAuthenticationStepRegistry.Entry<T>?
        = steps.find { it.step.fqdn == fqdn }
}
