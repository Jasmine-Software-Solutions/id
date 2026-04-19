package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.IPassword
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IAuthenticationFlowRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IPasswordRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.services.authentication.*

object EnterPasswordAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:enter_password",
    alternatives = setOf(PollMagicLinkAuthenticationFlowStep),
    level = 1
) {
    object Request
    data class Response(val email: String?, val password: String?)

    class Handler<Flow : IAuthenticationFlow, TAccount : IAccount, TPassword : IPassword>(
        val accountRepository: IAccountRepository<TAccount>,
        val tenantMembershipRepository: ITenantMembershipRepository<out ITenantMembership>,
        val passwordRepository: IPasswordRepository<TPassword>,
        val flowRepository: IAuthenticationFlowRepository<Flow>,
    ) : AuthenticationFlowStepHandler<Flow, Request, Response>(Request::class, Response::class) {
        override fun create(flow: Flow) = Request

        override fun accept(
            flow: Flow,
            request: Request,
            response: Response
        ): AuthenticationFlowStepResult {
            if ((flow.account == null && response.email == null) || response.password == null)
                return RetryAuthenticationFlowStepResult

            var account = flow.account
                ?: return RetryAuthenticationFlowStepResult

            if (response.email != null) {
                account = accountRepository.findByEmail(response.email)
                    ?: return RetryAuthenticationFlowStepResult

                if (flow.tenant != null) {
                    val member = tenantMembershipRepository.findByAccount(account)
                        .any { it.tenant == flow.tenant }

                    if (!member)
                        return RetryAuthenticationFlowStepResult
                }
            }

            flowRepository.update(flow) {
                this.account = account
            }

            val currentPassword = passwordRepository.findByAccount(account)
                .lastOrNull() ?: return RetryAuthenticationFlowStepResult

            if (currentPassword.verify(response.password))
                return ContinueAuthenticationFlowStepResult

            return RetryAuthenticationFlowStepResult
        }
    }
}