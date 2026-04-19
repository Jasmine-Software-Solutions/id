package com.jasminesoftwaresolutions.id.domain.registries

import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStepRenderer
import kotlin.reflect.KClass

interface IAuthenticationStepRendererRegistry<T : IAuthenticationFlow> {
    fun entries(): Map<KClass<*>, Map<AuthenticationFlowStep, AuthenticationFlowStepRenderer<T, *, *>>>

    open fun <TAgent : Any> entries(agentType: KClass<TAgent>): Map<AuthenticationFlowStep, AuthenticationFlowStepRenderer<T, TAgent, *>>
        = (entries()[agentType] ?: emptyMap()) as Map<AuthenticationFlowStep, AuthenticationFlowStepRenderer<T, TAgent, *>>

    open fun <TAgent : Any> findOrNull(agentType: KClass<TAgent>, step: AuthenticationFlowStep): AuthenticationFlowStepRenderer<T, TAgent, *>?
        = entries(agentType).entries.firstOrNull { it.key.fqdn == step.fqdn }?.value

    open fun <TAgent : Any> find(agentType: KClass<TAgent>, step: AuthenticationFlowStep): AuthenticationFlowStepRenderer<T, TAgent, *>
        = findOrNull(agentType, step)!!

    fun <TAgent : Any> register(agentType: KClass<TAgent>, step: AuthenticationFlowStep, renderer: AuthenticationFlowStepRenderer<T, TAgent, *>, force: Boolean = false)
}

open class AuthenticationStepRendererRegistry<T : IAuthenticationFlow> : IAuthenticationStepRendererRegistry<T> {
    protected val entries = mutableMapOf<KClass<*>, MutableMap<AuthenticationFlowStep, AuthenticationFlowStepRenderer<T, *, *>>>()

    override fun entries(): Map<KClass<*>, Map<AuthenticationFlowStep, AuthenticationFlowStepRenderer<T, *, *>>>
        = entries.toMap()

    override fun <TAgent : Any> register(
        agentType: KClass<TAgent>,
        step: AuthenticationFlowStep,
        renderer: AuthenticationFlowStepRenderer<T, TAgent, *>,
        force: Boolean
    ) {
        if (findOrNull(agentType, step) != null && !force) return

        entries.getOrPut(agentType) { mutableMapOf() }[step] = renderer
    }
}