package app.domain.services.authentication.steps

import app.domain.models.account.IHashedMagicLink
import app.domain.models.authentication.IAuthenticationFlow
import app.domain.repositories.IMagicLinkRepository
import app.domain.services.authentication.*
import app.infrastructure.etc.SecureToken
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

object PollMagicLinkAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:poll_magic_link",
    alternatives = setOf(EnterPasswordAuthenticationFlowStep),
    level = 1
) {
    data class Request(val id: UUID, val acceptanceToken: String)
    object Response

    class Handler(
        val magicLinkRepository: IMagicLinkRepository
    ) : IAuthenticationFlowStepHandler<Request, Response> {
        override fun create(flow: IAuthenticationFlow): Request {
            val acceptanceToken = SecureToken()
            val decisionToken = SecureToken()

            val magicLink = magicLinkRepository.create {
                this.expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
                this.account = flow.account!!

                (this as IHashedMagicLink).acceptanceToken = acceptanceToken
                this.decisionToken = decisionToken
            }

            TODO("Email magic link / create abstraction")
            return Request(magicLink.id, acceptanceToken)
        }

        override fun accept(
            flow: IAuthenticationFlow,
            request: Request,
            response: Response
        ): AuthenticationFlowStepResult {
            val magicLink = magicLinkRepository.findById(request.id)
                ?: return RetryAuthenticationFlowStepResult

            if (magicLink.consumed || !magicLink.approved)
                return RetryAuthenticationFlowStepResult

            magicLink.consumed = true

            return ContinueAuthenticationFlowStepResult
        }
    }
}