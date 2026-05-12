package app.infrastructure.services

import com.jasminesoftwaresolutions.id.domain.services.IEmailService
import com.resend.Resend
import com.resend.services.emails.model.Attachment
import com.resend.services.emails.model.CreateEmailOptions
import java.io.File

class ResendEmailService(
    private val apiKey: String,
    private val sender: Pair<String, String>
) : IEmailService {
    private val resend = Resend(apiKey)

    override fun send(to: String, subject: String, body: String, files: List<File>) {
        val sendEmailRequest = CreateEmailOptions.builder()
            .from("${sender.first} <${sender.second}>")
            .to(to)
            .subject(subject)
            .html(body)

        if (files.isNotEmpty()) {
            sendEmailRequest.attachments(files.map {
                Attachment.builder()
                    .fileName(it.name)
                    .content(it.readText())
                    .build()
            })
        }

        resend.emails().send(sendEmailRequest.build())
    }
}