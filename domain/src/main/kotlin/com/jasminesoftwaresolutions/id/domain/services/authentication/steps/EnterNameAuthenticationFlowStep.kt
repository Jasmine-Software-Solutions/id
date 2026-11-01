package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.events.AccountProfileUpdatedEvent
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.repositories.IAccountRepository
import com.jasminesoftwaresolutions.id.domain.services.authentication.*
import java.time.Instant

/**
 * Collects the first and last name for accounts that have none.
 *
 * Only accounts created without names — the magic-link sign-up path in
 * [EnterEmailAddressAuthenticationFlowStep] — qualify, so existing accounts and
 * invitation-created accounts (which require names) never see this step.
 */
object EnterNameAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:enter_name",
    level = 2
) {
    /** Matches the `accounts.first_name` / `accounts.last_name` column width. */
    const val NAME_MAX_LENGTH = 40

    object Request
    data class Response(val firstName: String?, val lastName: String?)

    class Handler(
        val accountRepository: IAccountRepository,
        val eventDispatcher: IEventDispatcher
    ) : AuthenticationFlowStepHandler<Request, Response>(Request::class, Response::class) {
        override fun create(flow: IAuthenticationFlow) = Request

        override fun accept(
            flow: IAuthenticationFlow,
            request: Request,
            response: Response
        ): AuthenticationFlowStepResult {
            val account = flow.account ?: return RetryAuthenticationFlowStepResult

            val firstName = response.firstName.normalisedName() ?: return RetryAuthenticationFlowStepResult
            val lastName = response.lastName.normalisedName() ?: return RetryAuthenticationFlowStepResult

            val previousFirstName = account.firstName
            val previousLastName = account.lastName

            accountRepository.update(account) {
                this.firstName = firstName
                this.lastName = lastName
            }

            eventDispatcher.dispatch(
                AccountProfileUpdatedEvent(
                    principalId = account.id,
                    action = IdActions.UPDATE_ACCOUNT_PROFILE,
                    resource = IdResources.account(account.id).toString(),
                    timestamp = Instant.now(),
                    accountId = account.id,
                    previousFirstName = previousFirstName,
                    newFirstName = firstName,
                    previousLastName = previousLastName,
                    newLastName = lastName,
                )
            )

            return ContinueAuthenticationFlowStepResult
        }

        private fun String?.normalisedName(): String?
            = this?.trim()?.takeUnless { it.isEmpty() || it.length > NAME_MAX_LENGTH }
    }
}
