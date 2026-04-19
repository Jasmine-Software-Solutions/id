package com.jasminesoftwaresolutions.id.domain.services.authentication

import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepHandlerRegistry
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.IAuthenticationFlowRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.ISessionService
import java.time.Instant
import kotlin.time.Duration

interface IAuthenticationService<T : IAuthenticationFlow> {
    fun start(): T

    fun T.handler(): AuthenticationFlowStepHandler<T, *, *>

    fun T.instead(step: AuthenticationFlowStep): Boolean
    fun <TRequest : Any, TResponse : Any> T.next(request: TRequest, response: TResponse): Pair<AuthenticationFlowStepResult, AuthenticationFlowStep?>

    fun T.finish(): AuthenticationFlowResult
}

open class StandardAuthenticationService<TFlow : IAuthenticationFlow, TSession: ISession>(
    protected val stepRegistry: IAuthenticationStepRegistry<TFlow>,
    protected val handlerRegistry: IAuthenticationStepHandlerRegistry<TFlow>,
    protected val flowRepository: IAuthenticationFlowRepository<TFlow>,
    protected val sessionService: ISessionService<TSession>,
    protected val lifetime: Duration
) : IAuthenticationService<TFlow> {
    override fun start(): TFlow {
        val flow = flowRepository.create {
            this.expiresAt = Instant.now().plusMillis(lifetime.inWholeMilliseconds)
        }

        val initialStep = stepRegistry.nextOrNull(flow)
            ?: throw IllegalStateException("No qualifying initial step registered.")

        flowRepository.update(flow) {
            steps.add(initialStep)
        }

        return flow
    }

    override fun TFlow.handler(): AuthenticationFlowStepHandler<TFlow, *, *> {
        val current = steps.current()
        return (handlerRegistry[current]
            ?: throw IllegalStateException("No handler found for " + current.fqdn))
    }

    override fun TFlow.instead(step: AuthenticationFlowStep): Boolean {
        val current = steps.current()
        if (current.alternatives.none { it.fqdn == step.fqdn })
            return false

        flowRepository.update(this) {
            steps.replace(step)
        }

        return true
    }

    override fun <TRequest : Any, TResponse : Any> TFlow.next(request: TRequest, response: TResponse): Pair<AuthenticationFlowStepResult, AuthenticationFlowStep?> {
        val current = steps.current()
        val handler = handler() as AuthenticationFlowStepHandler<TFlow, TRequest, TResponse>

        val result = handler.accept(this, request, response)

        if (result is RetryAuthenticationFlowStepResult) {
            flowRepository.update(this) {
                steps.add(current)
            }

            return result to current
        }

        if (result is BypassAuthenticationFlowStepResult)
            return result to null

        val next = stepRegistry.nextOrNull(this)
            ?: return result to null

        flowRepository.update(this) {
            steps.add(next)
        }

        return result to next
    }

    override fun TFlow.finish(): AuthenticationFlowResult {
        if (this.account == null) return AccountMissingAuthenticationFlowResult

        val level = this.steps.values.map { it.level }.distinct()
        for (x in 0 until level.max())
            if (!level.contains(x)) return LevelMissingAuthenticationFlowResult

        val session = sessionService.create(account!!)
        return AuthenticatedAuthenticationFlowResult(
            session
        )
    }
}