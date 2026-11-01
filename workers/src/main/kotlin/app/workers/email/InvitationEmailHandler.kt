package app.workers.email

import com.jasminesoftwaresolutions.id.domain.events.InvitationCreatedEvent

/**
 * Composes and sends a tenant invitation email in response
 * to an [InvitationCreatedEvent].
 */
class InvitationEmailHandler(
    private val sender: ResendEmailSender,
    private val templates: TemplateRenderer,
    private val baseUrl: String,
) {
    fun handle(event: InvitationCreatedEvent) {
        val acceptUrl = "$baseUrl/#/invitations/${event.invitationId}"
        val body = templates.render(
            "emails/invitation/accept.kte", mapOf(
                "acceptUrl" to acceptUrl,
                "tenantName" to event.tenantName,
            )
        )

        sender.send(event.email, "You've been invited to join ${event.tenantName} on Jasmine Identity", body)
    }
}
