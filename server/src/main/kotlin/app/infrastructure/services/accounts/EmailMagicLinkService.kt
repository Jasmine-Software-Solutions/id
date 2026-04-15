package app.infrastructure.services.accounts

import app.Env
import app.domain.models.account.IAccount
import app.domain.models.account.IHashedMagicLink
import app.domain.repositories.IMagicLinkRepository
import app.domain.services.IEmailService
import app.domain.services.accounts.IMagicLinkService
import app.infrastructure.etc.SecureToken
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class EmailMagicLinkService(
    val magicLinkRepository: IMagicLinkRepository,
    val lifetime: Duration = 5.minutes,
    val emailService: IEmailService,
    val templateEngine: TemplateEngine
) : IMagicLinkService<IHashedMagicLink> {
    override fun create(account: IAccount): IHashedMagicLink {
        val acceptanceToken = SecureToken()
        val decisionToken = SecureToken()

        val magicLink = magicLinkRepository.create {
            this.expiresAt = Instant.now().plus(lifetime.inWholeMilliseconds, ChronoUnit.MILLIS)

            (this as IHashedMagicLink).acceptanceToken = acceptanceToken
            this.decisionToken = decisionToken
        } as IHashedMagicLink

        val decisionUrl = Env.EXTERNAL_BASE_URL + "/magic_links/" + decisionToken

        val content = StringOutput()
        templateEngine.render("emails/magic_link/issued.kte",
            mapOf("decisionUrl" to decisionUrl),
            content)
        emailService.send(account.email, "Jasmine Sign-On Link", content.toString())

        return MagicLink(magicLink, acceptanceToken, decisionToken)
    }

    class MagicLink(
        private val magicLink: IHashedMagicLink,
        private val _acceptanceToken: String,
        private val _decisionToken: String
    ) : IHashedMagicLink {
        override val id: UUID
            get() = magicLink.id

        override val createdAt: Instant
            get() = magicLink.createdAt

        override var expiresAt: Instant
            get() = magicLink.expiresAt
            set(value) { magicLink.expiresAt = value }

        override var decidedAt: Instant?
            get() = magicLink.decidedAt
            set(value) { magicLink.decidedAt = value }

        override var account: IAccount
            get() = magicLink.account
            set(value) { magicLink.account = value }

        override var approved: Boolean
            get() = magicLink.approved
            set(value) { magicLink.approved = value }

        override var consumed: Boolean
            get() = magicLink.consumed
            set(value) { magicLink.consumed = value }

        override var acceptanceToken: String
            get() = _acceptanceToken
            set(value) { magicLink.acceptanceToken = value }

        override var decisionToken: String
            get() = _decisionToken
            set(value) { magicLink.decisionToken = value }

        override fun verifyAcceptanceToken(token: String): Boolean {
            return magicLink.verifyAcceptanceToken(token)
        }

        override fun verifyDecisionToken(token: String): Boolean {
            return magicLink.verifyDecisionToken(token)
        }
    }
}