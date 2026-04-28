package com.jasminesoftwaresolutions.idinterfaces.services.account

import com.jasminesoftwaresolutions.id.domain.models.account.IMagicLink
import com.jasminesoftwaresolutions.id.domain.repositories.IMagicLinkRepository
import java.time.Instant
import java.util.*

open class MagicLinkControllerService<T : IMagicLink>(
    protected val repository: IMagicLinkRepository<T>
) {
    open class PromptResult {
        class Invalid : PromptResult()
        class Expired : PromptResult()
        class AlreadyDecided(val approved: Boolean) : PromptResult()
        class Prompt(
            val accountEmail: String,
            val id: UUID,
            val token: String
        ) : PromptResult()
    }

    open fun prompt(id: UUID, token: String): PromptResult {
        val magicLink = repository.findById(id)
            ?.takeIf { it.verifyDecisionToken(token) }
            ?: return PromptResult.Invalid()

        if (magicLink.expiresAt.isBefore(Instant.now()))
            return PromptResult.Expired()

        if (magicLink.decidedAt != null)
            return PromptResult.AlreadyDecided(magicLink.approved)

        return PromptResult.Prompt(
            accountEmail = magicLink.account.email,
            id = magicLink.id,
            token = token
        )
    }

    open class DecideResult {
        class Invalid : DecideResult()
        class Expired : DecideResult()
        class AlreadyDecided(val approved: Boolean) : DecideResult()
        class Approved : DecideResult()
        class Denied : DecideResult()
    }

    open fun decide(id: UUID, token: String, approve: Boolean): DecideResult {
        val magicLink = repository.findById(id)
            ?.takeIf { it.verifyDecisionToken(token) }
            ?: return DecideResult.Invalid()

        if (magicLink.expiresAt.isBefore(Instant.now()))
            return DecideResult.Expired()

        if (magicLink.decidedAt != null)
            return DecideResult.AlreadyDecided(magicLink.approved)

        repository.update(magicLink) {
            approved = approve
            decidedAt = Instant.now()
        }

        return if (approve) DecideResult.Approved() else DecideResult.Denied()
    }
}
