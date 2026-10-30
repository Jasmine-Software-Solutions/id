package app.workers.email

import com.jasminesoftwaresolutions.id.domain.events.CredentialStuffingSucceededSecurityEvent
import org.slf4j.LoggerFactory

/**
 * Composes and sends a "your account was protected" email when a
 * credential stuffing attempt is detected via the haveibeenpwned.com API.
 */
class CredentialStuffingEmailHandler(
    private val sender: ResendEmailSender,
    private val templates: TemplateRenderer,
    private val baseUrl: String,
) {
    private val log = LoggerFactory.getLogger(CredentialStuffingEmailHandler::class.java)

    fun handle(event: CredentialStuffingSucceededSecurityEvent) {
        val resetUrl = "$baseUrl/account/reset-password"
        val body = templates.render(
            "emails/credential_stuffing/detected.kte",
            mapOf("resetUrl" to resetUrl),
        )

        sender.send(event.email, "Security Alert — Your Account Was Protected", body)
    }
}
