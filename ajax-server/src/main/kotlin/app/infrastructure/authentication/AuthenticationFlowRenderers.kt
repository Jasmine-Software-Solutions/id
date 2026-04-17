package app.infrastructure.authentication

import app.domain.models.authentication.IAuthenticationFlow
import app.domain.services.authentication.AuthenticationFlowStep
import app.domain.services.authentication.AuthenticationFlowStepRenderer
import app.domain.services.authentication.AuthenticationFlowStepResult
import app.infrastructure.etc.renderWithContext
import io.javalin.http.Context
import app.domain.services.authentication.steps.EnterEmailAddressAuthenticationFlowStep.Request as EnterEmailAddressRequest
import app.domain.services.authentication.steps.EnterPasswordAuthenticationFlowStep.Request as EnterPasswordRequest
import app.domain.services.authentication.steps.EnterTOTPAuthenticationFlowStep.Request as EnterTOTPRequest
import app.domain.services.authentication.steps.PollMagicLinkAuthenticationFlowStep.Request as PollMagicLinkRequest

class EnterEmailAddressAuthenticationFlowRenderer<T : IAuthenticationFlow> : AuthenticationFlowStepRenderer<T, Context, EnterEmailAddressRequest> {
    override fun render(
        agent: Context,
        flow: T,
        step: AuthenticationFlowStep,
        request: EnterEmailAddressRequest,
        source: AuthenticationFlowStepResult
    ) {
        agent.renderWithContext(
            "pages/login.kte",
            "usingPassword" to false,
            "email" to flow.account?.email,
        )
    }
}

class EnterPasswordAuthenticationFlowRenderer<T : IAuthenticationFlow> : AuthenticationFlowStepRenderer<T, Context, EnterPasswordRequest> {
    override fun render(
        agent: Context,
        flow: T,
        step: AuthenticationFlowStep,
        request: EnterPasswordRequest,
        source: AuthenticationFlowStepResult
    ) {
        agent.renderWithContext(
            "pages/login.kte",
            "usingPassword" to true,
            "email" to flow.account?.email
        )
    }
}

class EnterTOTPAuthenticationFlowRenderer<T : IAuthenticationFlow> : AuthenticationFlowStepRenderer<T, Context, EnterTOTPRequest> {
    override fun render(
        agent: Context,
        flow: T,
        step: AuthenticationFlowStep,
        request: EnterTOTPRequest,
        source: AuthenticationFlowStepResult
    ) {
        agent.renderWithContext("components/login/enter_otp.kte")
    }
}

class PollMagicLinkAuthenticationFlowRenderer<T : IAuthenticationFlow> : AuthenticationFlowStepRenderer<T, Context, PollMagicLinkRequest> {
    override fun render(
        agent: Context,
        flow: T,
        step: AuthenticationFlowStep,
        request: PollMagicLinkRequest,
        source: AuthenticationFlowStepResult
    ) {
        agent.renderWithContext(
            "components/magic_link/issued.kte",
            "email" to flow.account?.email,
            "magicLinkId" to request.id,
            "magicLinkToken" to request.acceptanceToken
        )
    }
}