package app.workers.email

import com.jasminesoftwaresolutions.id.domain.events.*
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/**
 * Worker that listens for email-sending domain events and dispatches emails
 * via the Resend API.
 *
 * This class is a thin orchestrator: it routes each event to the
 * appropriate handler. Template rendering lives in [TemplateRenderer],
 * and HTTP sending lives in [ResendEmailSender].
 *
 * Events consumed:
 * - [MagicLinkRequestedEvent] → sends magic link sign-in email
 * - [AccountEmailChangeStartedEvent] → sends new-email verification email
 * - [InvitationCreatedEvent] → sends tenant invitation email
 * - [CredentialStuffingSucceededSecurityEvent] → sends account protection notice email
 */
class EmailWorker(
    private val magicLink: MagicLinkEmailHandler,
    private val emailChange: EmailChangeEmailHandler,
    private val invitation: InvitationEmailHandler,
    private val credentialStuffing: CredentialStuffingEmailHandler,
) {
    private val log = LoggerFactory.getLogger(EmailWorker::class.java)

    val eventTypes: Map<String, KClass<out Event>> = mapOf(
        MagicLinkRequestedEvent::class.simpleName!! to MagicLinkRequestedEvent::class,
        AccountEmailChangeStartedEvent::class.simpleName!! to AccountEmailChangeStartedEvent::class,
        InvitationCreatedEvent::class.simpleName!! to InvitationCreatedEvent::class,
        CredentialStuffingSucceededSecurityEvent::class.simpleName!! to CredentialStuffingSucceededSecurityEvent::class,
    )

    fun handle(event: Event) {
        when (event) {
            is MagicLinkRequestedEvent -> magicLink.handle(event)
            is AccountEmailChangeStartedEvent -> emailChange.handle(event)
            is InvitationCreatedEvent -> invitation.handle(event)
            is CredentialStuffingSucceededSecurityEvent -> credentialStuffing.handle(event)
            else -> log.debug("EmailWorker ignoring event: ${event.eventType}")
        }
    }
}
