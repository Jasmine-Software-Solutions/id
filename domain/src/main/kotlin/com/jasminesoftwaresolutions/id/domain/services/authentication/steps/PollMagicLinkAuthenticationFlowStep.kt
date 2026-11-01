package com.jasminesoftwaresolutions.id.domain.services.authentication.steps

import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.MagicLinkRequestedEvent
import com.jasminesoftwaresolutions.id.domain.models.SecureToken
import com.jasminesoftwaresolutions.id.domain.models.account.IMagicLink
import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.repositories.IMagicLinkRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.IMagicLinkService
import com.jasminesoftwaresolutions.id.domain.services.authentication.*
import java.time.Instant
import java.util.*

object PollMagicLinkAuthenticationFlowStep : AuthenticationFlowStep(
    "com.jasminesoftwaresolutions.id:poll_magic_link",
    level = 1
) {
    data class Request(val sender: String, val id: UUID, val acceptanceToken: String)
    object Response

    override fun alternatives(): Set<AuthenticationFlowStep>
            = setOf(EnterPasswordAuthenticationFlowStep)

    class Handler(
        val magicLinkRepository: IMagicLinkRepository,
        val magicLinkService: IMagicLinkService,
        val eventDispatcher: IEventDispatcher,
        val sender: String
    ) : AuthenticationFlowStepHandler<Request, Response>(Request::class, Response::class) {
        override fun create(flow: IAuthenticationFlow): Request {
            if (flow.account == null)
                return Request(sender, UUID.randomUUID(), SecureToken())

            val magicLink = magicLinkService.create(flow.account!!)

            eventDispatcher.dispatch(
                MagicLinkRequestedEvent(
                    principalId = flow.account!!.id,
                    action = "id.jasmine.software:MagicLinkRequested",
                    resource = IdResources.platform().toString(),
                    timestamp = Instant.now(),
                    flowId = flow.id,
                    email = flow.account!!.email,
                    magicLinkId = magicLink.id,
                    token = magicLink.decisionToken,
                )
            )

            return Request(sender, magicLink.id, magicLink.acceptanceToken)
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

            magicLinkRepository.update(magicLink) {
                magicLink.consumed = true
            }

            return ContinueAuthenticationFlowStepResult
        }
    }
}