package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.models.account.IMagicLink
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.repositories.IMagicLinkRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.IMagicLinkService
import com.jasminesoftwaresolutions.id.domain.services.authentication.*
import java.util.*

object PollMagicLinkAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:poll_magic_link",
    alternatives = setOf(EnterPasswordAuthenticationFlowStep),
    level = 1
) {
    data class Request(val id: UUID, val acceptanceToken: String)
    object Response

    class Handler<Flow : IAuthenticationFlow, TMagicLink : IMagicLink>(
        val magicLinkRepository: IMagicLinkRepository<out TMagicLink>,
        val magicLinkService: IMagicLinkService<TMagicLink>,
    ) : AuthenticationFlowStepHandler<Flow, Request, Response>(Request::class, Response::class) {
        override fun create(flow: Flow): Request {
            if (flow.account == null)
                throw IllegalArgumentException()

            val magicLink = magicLinkService.create(flow.account!!)
            return Request(magicLink.id, magicLink.acceptanceToken)
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