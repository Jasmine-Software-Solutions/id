package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IMagicLink
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.repositories.IMagicLinkRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.IMagicLinkService
import com.jasminesoftwaresolutions.id.domain.services.authentication.*
import java.util.*

object PollMagicLinkAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:poll_magic_link",
    level = 1
) {
    data class Request(val sender: String, val id: UUID, val acceptanceToken: String)
    object Response

    override fun alternatives(): Set<AuthenticationFlowStep>
            = setOf(EnterPasswordAuthenticationFlowStep)

    class Handler<Flow : IAuthenticationFlow, TMagicLink : IMagicLink>(
        val magicLinkRepository: IMagicLinkRepository<out TMagicLink>,
        val magicLinkService: IMagicLinkService<TMagicLink>,
        val sender: String
    ) : AuthenticationFlowStepHandler<Flow, Request, Response>(Request::class, Response::class) {
        override fun create(flow: Flow): Request {
            if (flow.account == null)
                return Request(sender, UUID.randomUUID(), SecureToken())

            val magicLink = magicLinkService.create(flow.account!!)
            return Request(sender, magicLink.id, magicLink.acceptanceToken)
        }

        override fun accept(
            flow: Flow,
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