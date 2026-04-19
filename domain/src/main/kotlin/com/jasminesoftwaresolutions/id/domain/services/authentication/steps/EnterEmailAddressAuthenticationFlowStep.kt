package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IAuthenticationFlowRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.services.authentication.*

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
                ?: return ContinueAuthenticationFlowStepResult

            if (flow.tenant != null) {
                val member = tenantMembershipRepository.findByAccount(account)
                    .any { it.tenant == flow.tenant }

                if (!member)
                    return ContinueAuthenticationFlowStepResult
            }

            flowRepository.update(flow) {
                this.account = account
            }

            return ContinueAuthenticationFlowStepResult
        }
    }
}