package app.domain.services.authentication

import app.domain.models.authentication.IAuthenticationFlow
import app.domain.registries.IAuthenticationStepHandlerRegistry
import app.domain.registries.IAuthenticationStepRegistry
import app.domain.repositories.IAuthenticationFlowRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

interface IAuthenticationService {
    fun begin(): IAuthenticationFlow

    fun IAuthenticationFlow.current(): AuthenticationFlowStep
        = this.steps.values.last()

    fun <TRequest, TResponse> IAuthenticationFlow.next(request: TRequest, response: TResponse): Pair<AuthenticationFlowStepResult, AuthenticationFlowStep?>
}

open class StandardAuthenticationService(
    protected val stepRegistry: IAuthenticationStepRegistry,
    protected val handlerRegistry: IAuthenticationStepHandlerRegistry,
    protected val flowRepository: IAuthenticationFlowRepository
) : IAuthenticationService {
    override fun begin(): IAuthenticationFlow {
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

    override fun <TRequest, TResponse> IAuthenticationFlow.next(request: TRequest, response: TResponse): Pair<AuthenticationFlowStepResult, AuthenticationFlowStep?> {
        val current = current()
        val currentHandler = (handlerRegistry[current]
            ?: throw IllegalStateException("No handler found for " + current.fqdn))
            as IAuthenticationFlowStepHandler<TRequest, TResponse>

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
}