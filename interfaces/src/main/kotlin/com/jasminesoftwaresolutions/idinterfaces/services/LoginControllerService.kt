package com.jasminesoftwaresolutions.idinterfaces.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jasminesoftwaresolutions.id.domain.models.SignedValue
import com.jasminesoftwaresolutions.id.domain.models.account.ISession
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.registries.IAuthenticationStepRegistry
import com.jasminesoftwaresolutions.id.domain.repositories.IAuthenticationFlowRepository
import com.jasminesoftwaresolutions.id.domain.services.ISigningFunction
import com.jasminesoftwaresolutions.id.domain.services.authentication.*
import java.util.*

open class LoginControllerService<T : IAuthenticationFlow>(
    protected val stepRegistry: IAuthenticationStepRegistry<T>,
    protected val repository: IAuthenticationFlowRepository<T>,
    protected val service: IAuthenticationService<T>,
    protected val signingFunction: ISigningFunction
) {
    protected val gson = Gson()

    protected open fun newRequestForCurrentStep(flow: T): SignedValue<Any> {
        val handler = with (service) {
            flow.handler()
        }

        return SignedValue(
            signingFunction,
            handler.create(flow)
        )
    }

    inner class LoginResult(val flow: T, val step: AuthenticationFlowStep, val request: SignedValue<Any>)
    open fun login(): LoginResult {
        val flow = service.start()
        val step = flow.steps.currentOrNull()
            ?: throw IllegalStateException()

        return LoginResult(flow, step, newRequestForCurrentStep(flow))
    }

    open inner class NextResult
    open inner class ContinueFlowNextResult(val flow: T, val step: AuthenticationFlowStep, val request: SignedValue<*>) : NextResult()
    inner class RetryFlowNextResult(flow: T, step: AuthenticationFlowStep, request: SignedValue<*>) : ContinueFlowNextResult(flow, step, request)
    inner class AuthenticatedFlowNextResult(val flow: T, val session: ISession) : NextResult()
    inner class FlowNotFoundNextResult : NextResult()
    inner class SignatureMismatchNextResult : NextResult()

    open fun next(flowId: UUID, request: String, response: JsonObject): NextResult {
        val flow = repository.findById(flowId)
            ?: return FlowNotFoundNextResult()

        val handler = with (service) {
            flow.handler()
        }

        val request = try {
            SignedValue.fromString(signingFunction, request, handler.requestClass.java)
        } catch (e: IllegalArgumentException) {
            return SignatureMismatchNextResult()
        }

        val response = gson.fromJson(response, handler.responseClass.java)

        val result = with (service) {
            flow.next(request.value, response)
        }

        if (result.first is RetryAuthenticationFlowStepResult)
            return RetryFlowNextResult(flow, flow.steps.current(), request)

        if (result.first is BypassAuthenticationFlowStepResult || (result.first is ContinueAuthenticationFlowStepResult && result.second == null)) {
            val result = with (service) {
                flow.finish()
            }

            if (result !is AuthenticatedAuthenticationFlowResult)
                throw IllegalStateException()

            return AuthenticatedFlowNextResult(flow, result.session)
        }

        return ContinueFlowNextResult(flow, result.second!!, newRequestForCurrentStep(flow))
    }

    open inner class InsteadResult
    inner class ContinueFlowInsteadResult(val flow: IAuthenticationFlow, val step: AuthenticationFlowStep, val request: SignedValue<*>) : InsteadResult()
    inner class FlowNotFoundInsteadResult : InsteadResult()
    inner class StepNotFoundInsteadResult(val flow: IAuthenticationFlow) : InsteadResult()
    inner class IllegalStepInsteadResult(val flow: IAuthenticationFlow) : InsteadResult()

    open fun instead(flowId: UUID, fqdn: String): InsteadResult {
        val flow = repository.findById(flowId)
            ?: return FlowNotFoundInsteadResult()

        val step = stepRegistry.findByFqdn(fqdn)?.step
            ?: return StepNotFoundInsteadResult(flow)

        with (service) {
            if (!flow.instead(step))
                return IllegalStepInsteadResult(flow)

            return ContinueFlowInsteadResult(flow, step, newRequestForCurrentStep(flow))
        }
    }
}