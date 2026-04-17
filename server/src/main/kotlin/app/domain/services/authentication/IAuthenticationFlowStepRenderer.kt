package app.domain.services.authentication

import app.domain.models.authentication.IAuthenticationFlow

interface AuthenticationFlowStepRenderer<TFlow : IAuthenticationFlow, TAgent, TRequest> {
    fun render(agent: TAgent, flow: TFlow, step: AuthenticationFlowStep, request: TRequest, source: AuthenticationFlowStepResult)
}