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

    class Handler<T : ITOTPConfiguration>(
        val totpService: ITOTPService<T>
    ) : AuthenticationFlowStepHandler<Request, Response>(Request::class, Response::class) {
        override fun create(flow: IAuthenticationFlow) = Request

        override fun accept(
            flow: IAuthenticationFlow,
            request: Request,
            response: Response
        ): AuthenticationFlowStepResult {
            if (response.totp == null || flow.account == null)
                return RetryAuthenticationFlowStepResult

            val configuration = totpService.findByAccount(flow.account!!)

            val result = totpService.verify(configuration, response.totp)
            if (!result.valid)
                return RetryAuthenticationFlowStepResult

            return ContinueAuthenticationFlowStepResult
        }
    }
}