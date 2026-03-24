package app.infrastructure

import app.Mail
import app.application.LoginMagicLinkIssuer
import app.infrastructure.models.account.MagicLink
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput

class MailMagicLinkIssuer(
    private val templateEngine: TemplateEngine
) : LoginMagicLinkIssuer {
    override fun issue(magicLink: MagicLink) {
        val output = StringOutput()
        templateEngine.render("emails/magic_link/issued.kte",
            mapOf("magicLink" to magicLink), output)

        Mail.send(
            to = magicLink.account.email,
            subject = "Jasmine Sign-On Link",
            body = output.toString())
    }
}