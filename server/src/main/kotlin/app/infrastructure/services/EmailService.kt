package app.infrastructure.services

import com.jasminesoftwaresolutions.id.domain.services.IEmailService
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.io.File
import java.util.*

class EmailService(
    private val auth: Boolean,
    private val enableStartTLS: Boolean,
    private val host: String,
    private val port: Int,
    private val sslTrust: String,
    private val username: String,
    private val password: String,
    private val from: String,
) : IEmailService {
    private val session: Session

    init {
        val prop = Properties()
        prop["mail.smtp.auth"] = auth
        prop["mail.smtp.starttls.enable"] = enableStartTLS.toString()
        prop["mail.smtp.host"] = host
        prop["mail.smtp.port"] = port.toString()
        prop["mail.smtp.ssl.trust"] = sslTrust

        session = Session.getInstance(prop, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })
    }

    override fun send(to: String, subject: String, body: String, files: List<File>) {
        val message: Message = MimeMessage(session)
        message.setFrom(InternetAddress(from))
        message.setRecipients(
            Message.RecipientType.TO, InternetAddress.parse(to)
        )

        message.setSubject(subject)

        val multipart: Multipart = MimeMultipart()

        val mimeBodyPart = MimeBodyPart()
        mimeBodyPart.setContent(body, "text/html; charset=utf-8")
        multipart.addBodyPart(mimeBodyPart)

        for (file in files) {
            val attachmentBodyPart = MimeBodyPart()
            attachmentBodyPart.attachFile(file)
            multipart.addBodyPart(attachmentBodyPart)
        }

        message.setContent(multipart)
        Transport.send(message)
    }
}