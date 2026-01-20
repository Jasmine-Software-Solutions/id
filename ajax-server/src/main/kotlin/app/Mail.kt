package app

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.io.File
import java.util.*

object Mail {
    val session: Session

    init {
        val prop = Properties()
        prop["mail.smtp.auth"] = Env.SMTP_AUTH
        prop["mail.smtp.starttls.enable"] = Env.SMTP_STARTTLS_ENABLE.toString()
        prop["mail.smtp.host"] = Env.SMTP_HOST
        prop["mail.smtp.port"] = Env.SMTP_PORT.toString()
        prop["mail.smtp.ssl.trust"] = Env.SMTP_SSL_TRUST

        session = Session.getInstance(prop, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(Env.SMTP_USERNAME, Env.SMTP_PASSWORD)
            }
        })
    }

    fun send(to: String, subject: String, body: String, files: List<File> = listOf()) {
        val message: Message = MimeMessage(session)
        message.setFrom(InternetAddress(Env.SMTP_EMAIL))
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