package app.application

import app.infrastructure.account.AccountMagicLinkEngine
import app.infrastructure.models.account.MagicLink
import app.infrastructure.models.account.MagicLinksTable
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

data class MagicLinkCommand(
    val token: String,
    val decision: MagicLinkDecision?
)

sealed class MagicLinkDecision {
    object Allow : MagicLinkDecision()
    object Deny : MagicLinkDecision()
}

interface MagicLinkHandler {
    fun execute(command: MagicLinkCommand): MagicLinkResult
}

sealed class MagicLinkResult {
    data class Prompt(val accountEmail: String) : MagicLinkResult()
    data class Approved(val accountEmail: String) : MagicLinkResult()
    data class Denied(val accountEmail: String) : MagicLinkResult()
    object InvalidOrExpired : MagicLinkResult()
}

class MagicLinkService : MagicLinkHandler {
    override fun execute(command: MagicLinkCommand): MagicLinkResult = transaction {
        val link = MagicLink.find { MagicLinksTable.decisionToken eq command.token }.firstOrNull()
            ?: return@transaction MagicLinkResult.InvalidOrExpired

        if (link.expiresAt.isBefore(Instant.now())
            || link.decision != null) return@transaction MagicLinkResult.InvalidOrExpired

        val accountEmail = link.account.email

        if (command.decision == null)
            return@transaction MagicLinkResult.Prompt(accountEmail)

        return@transaction when (command.decision) {
            MagicLinkDecision.Allow -> {
                AccountMagicLinkEngine.decide(command.token, true)
                MagicLinkResult.Approved(accountEmail)
            }

            MagicLinkDecision.Deny -> {
                AccountMagicLinkEngine.decide(command.token, false)
                MagicLinkResult.Denied(accountEmail)
            }
        }
    }
}
