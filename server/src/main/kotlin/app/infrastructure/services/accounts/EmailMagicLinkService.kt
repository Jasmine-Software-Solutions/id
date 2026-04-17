package app.infrastructure.services.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IMagicLink
import app.domain.repositories.IMagicLinkRepository
import app.domain.services.IEmailService
import app.domain.services.accounts.StandardMagicLinkService
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import kotlin.time.Duration

class EmailMagicLinkService<T : IMagicLink>(
    repository: IMagicLinkRepository<T>,
    lifetime: Duration,
    private val emailService: IEmailService,
    private val templateEngine: TemplateEngine,
    private val externalBaseUrl: String
) : StandardMagicLinkService<T>(repository, lifetime) {
    override fun finish(account: IAccount, entity: T): T {
        val decisionUrl = externalBaseUrl + "/magic_links/" + entity.decisionToken

        val content = StringOutput()
        templateEngine.render("emails/magic_link/issued.kte",
            mapOf("decisionUrl" to decisionUrl),
            content)
        emailService.send(entity.account.email, "Jasmine Sign-On Link", content.toString())

        return super.finish(account, entity)
    }
}