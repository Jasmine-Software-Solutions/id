package app.domain.services.authentication.steps

import app.domain.models.account.IAccount
import app.domain.models.authentication.IAuthenticationFlow
import app.domain.models.tenant.ITenantMembership
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

    class Handler<Flow : IAuthenticationFlow, TAccount : IAccount, TTenantMembership : ITenantMembership>(
        val accountRepository: IAccountRepository<TAccount>,
        val tenantMembershipRepository: ITenantMembershipRepository<TTenantMembership>,
        val flowRepository: IAuthenticationFlowRepository<Flow>
    ) : AuthenticationFlowStepHandler<Flow, Request, Response>(Request::class, Response::class) {
        override fun create(flow: Flow) = Request

        override fun accept(
            flow: Flow,
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