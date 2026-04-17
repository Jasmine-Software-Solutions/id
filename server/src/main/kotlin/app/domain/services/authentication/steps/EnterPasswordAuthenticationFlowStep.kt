package app.domain.services.authentication.steps

import app.domain.models.account.IAccount
import app.domain.models.account.IPassword
import app.domain.models.authentication.IAuthenticationFlow
import app.domain.repositories.IAccountRepository
import app.domain.repositories.IAuthenticationFlowRepository
import app.domain.repositories.IPasswordRepository
import app.domain.services.authentication.*

object EnterPasswordAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:enter_password",
    alternatives = setOf(PollMagicLinkAuthenticationFlowStep),
    level = 1
) {
    object Request
    data class Response(val email: String?, val password: String?)

    class Handler<Flow : IAuthenticationFlow, TAccount : IAccount, TPassword : IPassword>(
        val accountRepository: IAccountRepository<TAccount>,
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

            val account = (if (response.email != null) accountRepository.findByEmail(response.email) else flow.account)
                ?: return RetryAuthenticationFlowStepResult

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