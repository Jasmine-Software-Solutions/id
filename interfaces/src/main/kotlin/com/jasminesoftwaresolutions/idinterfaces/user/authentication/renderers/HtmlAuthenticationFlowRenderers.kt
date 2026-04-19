package com.jasminesoftwaresolutions.idinterfaces.user.authentication.renderers

import com.jasminesoftwaresolutions.id.domain.models.SignedValue
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStep
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStepRenderer
import com.jasminesoftwaresolutions.id.domain.services.authentication.AuthenticationFlowStepResult
import com.jasminesoftwaresolutions.id.domain.services.authentication.RetryAuthenticationFlowStepResult
import com.jasminesoftwaresolutions.idinterfaces.renderWithContext
import io.javalin.http.Context

import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterEmailAddressAuthenticationFlowStep.Request as EnterEmailAddressRequest
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterPasswordAuthenticationFlowStep.Request as EnterPasswordRequest
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.EnterTOTPAuthenticationFlowStep.Request as EnterTOTPRequest
import com.jasminesoftwaresolutions.id.domain.services.authentication.steps.PollMagicLinkAuthenticationFlowStep.Request as PollMagicLinkRequest

class HTMLEnterEmailAddressAuthenticationFlowRenderer<T : IAuthenticationFlow> : AuthenticationFlowStepRenderer<T, Context, EnterEmailAddressRequest> {
    override fun render(
        agent: Context,
        flow: T,
        step: AuthenticationFlowStep,
        request: SignedValue<EnterEmailAddressRequest>,
        source: AuthenticationFlowStepResult
    ) {
        agent.renderWithContext(
            "pages/login.kte",
            "flowId" to flow.id,
            "request" to request,
            "usingPassword" to false
        )
    }
}

class HTMLEnterPasswordAuthenticationFlowRenderer<T : IAuthenticationFlow> : AuthenticationFlowStepRenderer<T, Context, EnterPasswordRequest> {
    override fun render(
        agent: Context,
        flow: T,
        step: AuthenticationFlowStep,
        request: SignedValue<EnterPasswordRequest>,
        source: AuthenticationFlowStepResult
    ) {
        agent.renderWithContext(
            "pages/login.kte",
            "flowId" to flow.id,
            "request" to request,
            "usingPassword" to true,
            "error" to (source is RetryAuthenticationFlowStepResult)
        )
    }
}

class HTMLEnterTOTPAuthenticationFlowRenderer<T : IAuthenticationFlow> : AuthenticationFlowStepRenderer<T, Context, EnterTOTPRequest> {
    override fun render(
        agent: Context,
        flow: T,
        step: AuthenticationFlowStep,
        request: SignedValue<EnterTOTPRequest>,
        source: AuthenticationFlowStepResult
    ) {
        agent.renderWithContext("components/login/enter_otp.kte")
    }
}

class HTMLPollMagicLinkAuthenticationFlowRenderer<T : IAuthenticationFlow> : AuthenticationFlowStepRenderer<T, Context, PollMagicLinkRequest> {
    override fun render(
        agent: Context,
        flow: T,
        step: AuthenticationFlowStep,
        request: SignedValue<PollMagicLinkRequest>,
        source: AuthenticationFlowStepResult
    ) {
        agent.renderWithContext(
            "components/magic_link/issued.kte",
            "email" to flow.account?.email,
            "magicLinkId" to request.value.id,
            "magicLinkToken" to request.value.acceptanceToken
        )
    }
}