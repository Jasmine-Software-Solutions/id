package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.InvalidPasswordAtAuthenticationEvent
import com.jasminesoftwaresolutions.id.domain.events.ValidPasswordAtAuthenticationEvent
import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IPassword
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IAuthenticationFlowRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IPasswordRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.services.authentication.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

object EnterPasswordAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:enter_password",
    level = 1
) {
    private val NULL_PASSWORD = SecureToken()

    object Request
    data class Response(val email: String?, val password: String?)

    override fun alternatives(): Set<AuthenticationFlowStep>
            = setOf(PollMagicLinkAuthenticationFlowStep)

    class Handler(
        val accountRepository: IAccountRepository,
        val tenantMembershipRepository: ITenantMembershipRepository,
        val passwordRepository: IPasswordRepository,
        val flowRepository: IAuthenticationFlowRepository,
        val eventDispatcher: IEventDispatcher,
        val dummyPassword: IPassword,
    ) : AuthenticationFlowStepHandler<Request, Response>(Request::class, Response::class) {
        override fun create(flow: IAuthenticationFlow) = Request

        private fun retry(password: String) = RetryAuthenticationFlowStepResult.also {
            dummyPassword.verify(password)
        }

        override fun accept(
            flow: IAuthenticationFlow,
            request: Request,
            response: Response
        ): AuthenticationFlowStepResult {
            val password = response.password
            if ((flow.account == null && response.email == null) || password == null)
                return retry(password ?: NULL_PASSWORD)

            var account = flow.account
                ?: return retry(password)

            if (response.email != null) {
                account = accountRepository.findByEmail(response.email)
                    ?: return retry(password)

                if (flow.tenant != null) {
                    val member = tenantMembershipRepository.findByAccount(account)
                        .any { it.tenant == flow.tenant }

                    if (!member)
                        return retry(password)
                }

                flowRepository.update(flow) {
                    this.account = account
                }
            }

            val currentPassword = passwordRepository.findByAccount(account)
                .lastOrNull() ?: return retry(password)

            if (currentPassword.verify(response.password)) {
                val sha1 = MessageDigest.getInstance("SHA-1")
                    .digest(password.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                    .uppercase()

                eventDispatcher.dispatch(
                    ValidPasswordAtAuthenticationEvent(
                        principalId = account.id,
                        action = "id.jasmine.software:ValidPasswordAtAuthentication",
                        resource = IdResources.platform().toString(),
                        timestamp = Instant.now(),
                        flowId = flow.id,
                        accountId = account.id,
                        email = account.email,
                        ipAddress = flow.ipAddress,
                        passwordHash = sha1,
                    )
                )

                return ContinueAuthenticationFlowStepResult
            }

            eventDispatcher.dispatch(
                InvalidPasswordAtAuthenticationEvent(
                    principalId = account.id,
                    action = "id.jasmine.software:InvalidPasswordAtAuthentication",
                    resource = IdResources.platform().toString(),
                    timestamp = Instant.now(),
                    flowId = flow.id,
                    accountId = account.id,
                    email = account.email,
                    ipAddress = flow.ipAddress,
                )
            )

            return RetryAuthenticationFlowStepResult
        }
    }
}
