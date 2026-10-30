package com.jasminesoftwaresolutions.id.domain.services.authentication

import com.jasminesoftwaresolutions.id.domain.events.AuthenticationFlowStartedEvent
import com.jasminesoftwaresolutions.id.domain.events.AuthenticationStepCompletedEvent
import com.jasminesoftwaresolutions.id.domain.events.AuthenticationSucceededEvent
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.SessionCreatedEvent
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepHandlerRegistry
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.IAuthenticationFlowRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.ISessionService
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration

interface IAuthenticationService {
    fun start(userAgent: String?, ipAddress: String): IAuthenticationFlow

    fun IAuthenticationFlow.handler(): AuthenticationFlowStepHandler<*, *>

    fun IAuthenticationFlow.instead(step: AuthenticationFlowStep): Boolean
    fun <TRequest : Any, TResponse : Any> IAuthenticationFlow.next(request: TRequest, response: TResponse): Pair<AuthenticationFlowStepResult, AuthenticationFlowStep?>

    fun IAuthenticationFlow.finish(): AuthenticationFlowResult

    /** Call this after a step's `create()` to persist the handler-generated state. */
    fun IAuthenticationFlow.setState(state: String?)

    /** Read the persisted state for the current step. */
    fun IAuthenticationFlow.getState(): String?
}

open class StandardAuthenticationService(
    protected val stepRegistry: IAuthenticationStepRegistry,
    protected val handlerRegistry: IAuthenticationStepHandlerRegistry,
    protected val flowRepository: IAuthenticationFlowRepository,
    protected val sessionService: ISessionService,
    protected val eventDispatcher: IEventDispatcher,
    protected val lifetime: Duration
) : IAuthenticationService {
    override fun start(userAgent: String?, ipAddress: String): IAuthenticationFlow {
        val flow = flowRepository.create {
            this.expiresAt = Instant.now().plusMillis(lifetime.inWholeMilliseconds)

            this.userAgent = userAgent
            this.ipAddress = ipAddress
        }

        val initialStep = stepRegistry.nextOrNull(flow)
            ?: throw IllegalStateException("No qualifying initial step registered.")

        flowRepository.update(flow) {
            steps.add(initialStep)
        }

        eventDispatcher.dispatch(
            AuthenticationFlowStartedEvent(
                principalId = UUID(0, 0),
                action = "id.jasmine.software:StartAuthenticationFlow",
                resource = IdResources.platform().toString(),
                timestamp = Instant.now(),
                flowId = flow.id,
                initialStep = initialStep.fqdn,
                ipAddress = ipAddress,
            )
        )

        return flow
    }

    override fun IAuthenticationFlow.handler(): AuthenticationFlowStepHandler<*, *> {
        val current = steps.current()
        return (handlerRegistry[current]
            ?: throw IllegalStateException("No handler found for " + current.fqdn))
    }

    override fun IAuthenticationFlow.instead(step: AuthenticationFlowStep): Boolean {
        val current = steps.current()
        if (current.alternatives().none { it.fqdn == step.fqdn })
            return false

        flowRepository.update(this) {
            steps.replace(step)
            state = null
        }

        return true
    }

    override fun <TRequest : Any, TResponse : Any> IAuthenticationFlow.next(request: TRequest, response: TResponse): Pair<AuthenticationFlowStepResult, AuthenticationFlowStep?> {
        val current = steps.current()
        val handler = handler() as AuthenticationFlowStepHandler<TRequest, TResponse>

        val result = handler.accept(this, request, response)

        if (result is RetryAuthenticationFlowStepResult) {
            // Don't re-add the step — just stay on it. State is preserved.
            return result to current
        }

        // Clear state when leaving the step (on success or bypass)
        flowRepository.update(this) {
            state = null
        }

        if (result is BypassAuthenticationFlowStepResult)
            return result to null

        // A tenant-scoped flow can reach here with no account: the email did not resolve to a
        // member. The step still completes, but there is no principal to attribute it to.
        account?.let {
            eventDispatcher.dispatch(
                AuthenticationStepCompletedEvent(
                    principalId = it.id,
                    action = "id.jasmine.software:AuthenticationStepCompleted",
                    resource = IdResources.platform().toString(),
                    timestamp = Instant.now(),
                    flowId = this.id,
                    step = current.fqdn,
                    accountId = it.id,
                    email = it.email,
                )
            )
        }

        val next = stepRegistry.nextOrNull(this)
            ?: return result to null

        flowRepository.update(this) {
            steps.add(next)
        }

        return result to next
    }

    override fun IAuthenticationFlow.finish(): AuthenticationFlowResult {
        if (this.account == null) return AccountMissingAuthenticationFlowResult

        val level = this.steps.values.map { it.level }.distinct()
        for (x in 0 until level.max())
            if (!level.contains(x)) return LevelMissingAuthenticationFlowResult

        val session = sessionService.create(account!!) {
            this.userAgent = this@finish.userAgent
            this.ipAddress = this@finish.ipAddress
        }

        eventDispatcher.dispatch(
            AuthenticationSucceededEvent(
                principalId = account!!.id,
                action = "id.jasmine.software:AuthenticationSucceeded",
                resource = IdResources.account(account!!.id).toString(),
                timestamp = Instant.now(),
                flowId = this.id,
                accountId = account!!.id,
                email = account!!.email,
                sessionId = session.id,
                ipAddress = this.ipAddress,
                userAgent = this.userAgent,
            )
        )

        eventDispatcher.dispatch(
            SessionCreatedEvent(
                principalId = account!!.id,
                action = "id.jasmine.software:SessionCreated",
                resource = IdResources.session(account!!.id, session.id).toString(),
                timestamp = Instant.now(),
                sessionId = session.id,
                accountId = account!!.id,
                ipAddress = this.ipAddress,
                userAgent = this.userAgent,
                expiresAt = session.expiresAt,
            )
        )

        return AuthenticatedAuthenticationFlowResult(session)
    }

    override fun IAuthenticationFlow.setState(s: String?) {
        flowRepository.update(this) {
            state = s
        }
    }

    override fun IAuthenticationFlow.getState(): String? = state
}
