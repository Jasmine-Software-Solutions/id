package app.domain.services.authentication

import app.domain.models.account.ISession
import app.domain.models.authentication.IAuthenticationFlow
import app.domain.registries.IAuthenticationStepHandlerRegistry
import app.domain.registries.IAuthenticationStepRegistry
import app.domain.repositories.IAuthenticationFlowRepository
import app.domain.services.accounts.ISessionService
import java.time.Instant
import java.time.temporal.ChronoUnit

interface IAuthenticationService<T : IAuthenticationFlow> {
    fun start(): T

    fun T.current(): AuthenticationFlowStep
        = this.steps.values.last()

    fun <TRequest : Any, TResponse : Any> T.next(request: TRequest, response: TResponse): Pair<AuthenticationFlowStepResult, AuthenticationFlowStep?>

    fun T.finish(): AuthenticationFlowResult
}

open class StandardAuthenticationService<TFlow : IAuthenticationFlow, TSession: ISession>(
    protected val stepRegistry: IAuthenticationStepRegistry<TFlow>,
    protected val handlerRegistry: IAuthenticationStepHandlerRegistry<TFlow>,
    protected val flowRepository: IAuthenticationFlowRepository<TFlow>,
    protected val sessionService: ISessionService<TSession>
) : IAuthenticationService<TFlow> {
    override fun start(): TFlow {
        val flow = flowRepository.create {
            this.expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        }

        val initialStep = stepRegistry.firstOrNull(flow)
            ?: throw IllegalStateException("No qualifying initial step registered.")

        flowRepository.update(flow) {
            steps.add(initialStep)
        }

        return flow
    }

    override fun <TRequest : Any, TResponse : Any> TFlow.next(request: TRequest, response: TResponse): Pair<AuthenticationFlowStepResult, AuthenticationFlowStep?> {
        val current = current()
        val currentHandler = (handlerRegistry[current]
            ?: throw IllegalStateException("No handler found for " + current.fqdn))
            as AuthenticationFlowStepHandler<TFlow, TRequest, TResponse>

        val result = currentHandler.accept(this, request, response)

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
        return AuthenticatedAuthenticationFlowResult(session)
    }
}