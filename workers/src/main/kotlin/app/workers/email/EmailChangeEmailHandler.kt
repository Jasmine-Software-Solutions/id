package app.workers.email

import com.jasminesoftwaresolutions.id.domain.events.AccountEmailChangeStartedEvent

/**
 * Composes and sends an email change verification email in response
 * to an [AccountEmailChangeStartedEvent].
 */
class EmailChangeEmailHandler(
    private val sender: ResendEmailSender,
    private val templates: TemplateRenderer,
    private val baseUrl: String,
) {
    fun handle(event: AccountEmailChangeStartedEvent) {
        val verifyUrl = "$baseUrl/#/email-change/verify?changeId=${event.accountId}&newEmail=${event.newEmail}"
        val body = templates.render(
            "emails/email_change/verify.kte", mapOf(
                "verifyUrl" to verifyUrl,
                "newEmail" to event.newEmail,
            )
        )

        sender.send(event.newEmail, "Verify your email address", body)
    }
}
