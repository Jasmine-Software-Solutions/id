package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.InvalidTOTPAtAuthenticationEvent
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.services.accounts.ITOTPService
import com.jasminesoftwaresolutions.id.domain.services.authentication.*
import java.time.Instant

object EnterTOTPAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:enter_totp",
    level = 2
) {
    object Request
    data class Response(val totp: Int?)

    class Handler(
        val totpService: ITOTPService,
        val eventDispatcher: IEventDispatcher
    ) : AuthenticationFlowStepHandler<Request, Response>(Request::class, Response::class) {
        override fun create(flow: IAuthenticationFlow) = Request

        override fun accept(
            flow: IAuthenticationFlow,
            request: Request,
            response: Response
        ): AuthenticationFlowStepResult {
            if (response.totp == null || flow.account == null)
                return RetryAuthenticationFlowStepResult

            val account = flow.account!!
            val configuration = totpService.findByAccount(account)

            val result = totpService.verify(configuration, response.totp)
            if (!result.valid) {
                eventDispatcher.dispatch(
                    InvalidTOTPAtAuthenticationEvent(
                        principalId = account.id,
                        action = "id.jasmine.software:InvalidTOTPAtAuthentication",
                        resource = IdResources.platform().toString(),
                        timestamp = Instant.now(),
                        flowId = flow.id,
                        accountId = account.id,
                        ipAddress = flow.ipAddress,
                    )
                )

                return RetryAuthenticationFlowStepResult
            }

            return ContinueAuthenticationFlowStepResult
        }
    }
}