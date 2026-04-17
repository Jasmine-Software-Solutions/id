package app.domain.services.authentication.steps

import app.domain.models.account.ITOTPConfiguration
import app.domain.models.authentication.IAuthenticationFlow
import app.domain.services.accounts.ITOTPService
import app.domain.services.authentication.*

object EnterTOTPAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:enter_totp",
    level = 2
) {
    object Request
    data class Response(val totp: Int?)

    class Handler<Flow : IAuthenticationFlow, TTOTPConfiguration : ITOTPConfiguration>(
        val totpService: ITOTPService<TTOTPConfiguration>
    ) : AuthenticationFlowStepHandler<Flow, Request, Response>(Request::class, Response::class) {
        override fun create(flow: Flow) = Request

        override fun accept(
            flow: Flow,
            request: Request,
            response: Response
        ): AuthenticationFlowStepResult {
            if (response.totp == null || flow.account == null)
                return RetryAuthenticationFlowStepResult

            val configuration = totpService.findByAccount(flow.account!!)
                ?: return RetryAuthenticationFlowStepResult

            val result = totpService.verify(configuration, response.totp)
            if (!result.valid)
                return RetryAuthenticationFlowStepResult

            return ContinueAuthenticationFlowStepResult
        }
    }
}