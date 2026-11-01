package app.workers.email

import com.google.gson.Gson
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Sends emails via the [Resend API](https://resend.com/docs/api-reference/emails/send-email).
 */
class ResendEmailSender(
    private val apiKey: String,
    private val senderName: String,
    private val senderEmail: String,
    private val http: HttpClient = HttpClient.newHttpClient(),
    private val gson: Gson = Gson(),
) {
    private val log = LoggerFactory.getLogger(ResendEmailSender::class.java)

    private data class ResendRequest(
        val from: String,
        val to: List<String>,
        val subject: String,
        val html: String,
    )

    fun send(to: String, subject: String, htmlBody: String) {
        // Without an API key there is nowhere to send from (dev stacks leave it blank). Log
        // the mail instead of POSTing and swallowing a 401 — the body carries the magic-link,
        // invitation and email-change URLs the operator needs to follow.
        if (apiKey.isBlank()) {
            log.info("[dev-mail] to=$to subject=$subject\n$htmlBody")
            return
        }

        val from = "$senderName <$senderEmail>"
        val requestBody = ResendRequest(
            from = from,
            to = listOf(to),
            subject = subject,
            html = htmlBody,
        )

        val json = gson.toJson(requestBody)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.resend.com/emails"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                log.info("Email sent to $to: $subject")
            } else {
                log.error("Resend API error (${response.statusCode()}): ${response.body()}")
            }
        } catch (e: Exception) {
            log.error("Failed to send email to $to: ${e.message}", e)
        }
    }
}
