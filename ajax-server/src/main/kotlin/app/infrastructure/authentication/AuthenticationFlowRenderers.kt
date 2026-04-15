package app.infrastructure.authentication

import app.domain.models.authentication.IAuthenticationFlow
import app.domain.services.authentication.AuthenticationFlowStepRenderer
import app.domain.services.authentication.AuthenticationFlowStepResult
import app.infrastructure.etc.renderWithContext
import io.javalin.http.Context
import app.domain.services.authentication.steps.EnterEmailAddressAuthenticationFlowStep.Request as EnterEmailAddressRequest
import app.domain.services.authentication.steps.EnterPasswordAuthenticationFlowStep.Request as EnterPasswordRequest
import app.domain.services.authentication.steps.EnterTOTPAuthenticationFlowStep.Request as EnterTOTPRequest
import app.domain.services.authentication.steps.PollMagicLinkAuthenticationFlowStep.Request as PollMagicLinkRequest

object EnterEmailAddressAuthenticationFlowRenderer : AuthenticationFlowStepRenderer<Context, EnterEmailAddressRequest> {
    override fun render(
        agent: Context,
        flow: IAuthenticationFlow,
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

object EnterPasswordAuthenticationFlowRenderer : AuthenticationFlowStepRenderer<Context, EnterPasswordRequest> {
    override fun render(
        agent: Context,
        flow: IAuthenticationFlow,
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

object EnterTOTPAuthenticationFlowRenderer : AuthenticationFlowStepRenderer<Context, EnterTOTPRequest> {
    override fun render(
        agent: Context,
        flow: IAuthenticationFlow,
        request: EnterTOTPRequest,
        source: AuthenticationFlowStepResult
    ) {
        agent.renderWithContext("components/login/enter_otp.kte")
    }
}

object PollMagicLinkAuthenticationFlowRenderer : AuthenticationFlowStepRenderer<Context, PollMagicLinkRequest> {
    override fun render(
        agent: Context,
        flow: IAuthenticationFlow,
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