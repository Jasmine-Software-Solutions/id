package app.domain.services.authentication

import app.domain.models.authentication.IAuthenticationFlow

interface AuthenticationFlowStepRenderer<TAgent, TRequest> {
    fun render(agent: TAgent, flow: IAuthenticationFlow, request: TRequest, source: AuthenticationFlowStepResult)
}