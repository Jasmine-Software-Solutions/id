package com.jasminesoftwaresolutions.id.domain.services.authentication

import com.jasminesoftwaresolutions.id.domain.models.SignedValue
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow

interface AuthenticationFlowStepRenderer<TFlow : IAuthenticationFlow, TAgent : Any, TRequest : Any> {
    fun render(agent: TAgent, flow: TFlow, step: AuthenticationFlowStep, request: SignedValue<TRequest>, source: AuthenticationFlowStepResult)
}