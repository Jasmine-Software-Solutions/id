package app.domain.services.authentication

import app.domain.models.authentication.IAuthenticationFlow

open class AuthenticationFlowStep(
    val fqdn: String,
    val alternatives: Set<AuthenticationFlowStep> = emptySet(),
    val level: Int = 0
)

interface IAuthenticationFlowStepHandler<TRequest, TResponse> {
    fun create(flow: IAuthenticationFlow): TRequest
    fun accept(flow: IAuthenticationFlow, request: TRequest, response: TResponse): AuthenticationFlowStepResult
}