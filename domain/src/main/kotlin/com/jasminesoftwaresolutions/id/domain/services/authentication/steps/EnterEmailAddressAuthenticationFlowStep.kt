package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.events.AccountCreatedEvent
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenantMembership
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.repositories.IAuthenticationFlowRepository
import com.jasminesoftwaresolutions.id.domain.repositories.ITenantMembershipRepository
import com.jasminesoftwaresolutions.id.domain.services.authentication.*
import java.time.Instant

object EnterEmailAddressAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:enter_email_address"
) {
    object Request
    data class Response(val email: String?)

    override fun alternatives(): Set<AuthenticationFlowStep>
        = setOf(EnterPasswordAuthenticationFlowStep)

    class Handler(
        val accountRepository: IAccountRepository,
        val tenantMembershipRepository: ITenantMembershipRepository,
        val flowRepository: IAuthenticationFlowRepository,
        val eventDispatcher: IEventDispatcher
    ) : AuthenticationFlowStepHandler<Request, Response>(Request::class, Response::class) {
        override fun create(flow: IAuthenticationFlow) = Request

        override fun accept(
            flow: IAuthenticationFlow,
            request: Request,
            response: Response
        ): AuthenticationFlowStepResult {
            if (response.email == null)
                return RetryAuthenticationFlowStepResult

            val account = findOrCreate(flow, response.email)
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

        /**
         * Resolves the typed email to an account, creating one on the plain sign-in path.
         *
         * Signing in by magic link is also how accounts are created here: the applicant types
         * their email at the login page and the account exists by the time they approve the
         * link. The name step collects the names afterwards.
         *
         * Lookup happens as typed first, then lowercased — `accounts.email` carries a
         * case-sensitive unique index, so a case variant must resolve to the existing row
         * rather than attempt a duplicate insert. The row is created with the lowercased form.
         *
         * Tenant-scoped flows (invitations) keep their previous semantics: an unknown email
         * simply resolves to no account.
         */
        private fun findOrCreate(flow: IAuthenticationFlow, typedEmail: String): IAccount? {
            accountRepository.findByEmail(typedEmail)?.let { return it }

            val email = typedEmail.lowercase()
            accountRepository.findByEmail(email)?.let { return it }

            if (flow.tenant != null) return null

            val account = accountRepository.create {
                this.email = email
                // Blank names mark an account that has not completed the name step yet.
                this.firstName = ""
                this.lastName = ""
            }

            // Same dispatch as the invitation path. This is functional, not only audit:
            // WitchwoodSyncer consumes AccountCreatedEvent to ensure the account's Witchwood
            // principal, which is the prerequisite for any policy grant.
            eventDispatcher.dispatch(
                AccountCreatedEvent(
                    principalId = account.id,
                    action = IdActions.CREATE_ACCOUNT,
                    resource = IdResources.account(account.id).toString(),
                    timestamp = Instant.now(),
                    accountId = account.id,
                    email = account.email,
                    firstName = account.firstName,
                    lastName = account.lastName,
                    emailVerified = false,
                    ipAddress = flow.ipAddress,
                )
            )

            return account
        }
    }
}
