package app.infrastructure

import app.Mail
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput

class ForgotPasswordEmailSender(val templateEngine: TemplateEngine) {
    fun send(to: String, resetCode: String) {
        val templateOutput = StringOutput()
        templateEngine.render("emails/forgot_password.kte", resetCode, templateOutput)

        val emailBody = templateOutput.toString()
        Mail.send(
            to = to,
            subject = "[URGENT] Password Reset Request",
            body = emailBody
        )
    }
}
