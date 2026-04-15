package app.domain.services.authentication.steps

import app.domain.models.authentication.IAuthenticationFlow
import app.domain.repositories.IAccountRepository
import app.domain.repositories.IAuthenticationFlowRepository
import app.domain.repositories.ITenantMembershipRepository
import app.domain.services.authentication.*

object EnterEmailAddressAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:enter_email_address",
    alternatives = setOf()
) {
    object Request
    data class Response(val email: String?)

    class Handler(
        val accountRepository: IAccountRepository,
        val tenantMembershipRepository: ITenantMembershipRepository,
        val flowRepository: IAuthenticationFlowRepository
    ) : AuthenticationFlowStepHandler<Request, Response>(Request::class, Response::class) {
        override fun create(flow: IAuthenticationFlow) = Request

        override fun accept(
            flow: IAuthenticationFlow,
            request: Request,
            response: Response
        ): AuthenticationFlowStepResult {
            if (response.email == null)
                return RetryAuthenticationFlowStepResult

            val account = accountRepository.findByEmail(response.email)
                ?: return RetryAuthenticationFlowStepResult

            if (flow.tenant != null) {
                val member = tenantMembershipRepository.findByAccount(account)
                    .any { it.tenant == flow.tenant }

                if (!member)
                    return RetryAuthenticationFlowStepResult
            }

            flowRepository.update(flow) {
                this.account = account
            }

            return ContinueAuthenticationFlowStepResult
        }
    }
}