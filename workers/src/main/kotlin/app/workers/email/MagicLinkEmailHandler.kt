package app.workers.email

import com.jasminesoftwaresolutions.id.domain.events.MagicLinkRequestedEvent
import org.slf4j.LoggerFactory

/**
 * Composes and sends a magic link sign-in email in response to a
 * [MagicLinkRequestedEvent].
 */
class MagicLinkEmailHandler(
    private val sender: ResendEmailSender,
    private val templates: TemplateRenderer,
    private val baseUrl: String,
) {
    fun handle(event: MagicLinkRequestedEvent) {
        val token = event.token

        val decisionUrl = "$baseUrl/#/magic-links/${event.magicLinkId}?token=$token"
        val body = templates.render("emails/magic_link/issued.kte", mapOf("decisionUrl" to decisionUrl))

        sender.send(event.email, "Jasmine ID Sign-On Link", body)
    }
}
