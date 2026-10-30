package app.workers.email

import app.workers.WorkerModule
import app.workers.infrastructure.SqsClientFactory
import app.workers.infrastructure.SqsEventListener
import app.workers.infrastructure.WorkerModuleName
import org.slf4j.LoggerFactory

/**
 * Sends the ID platform's transactional mail in response to events on `id-email`.
 *
 * Holds no resources beyond its listener.
 */
class EmailModule(private val env: EmailWorkerEnv = EmailWorkerEnv.Default) : WorkerModule {

    override val name = WorkerModuleName.EMAIL
    override val queueUrl: String = env.SQS_QUEUE_URL_EMAIL

    private var listener: SqsEventListener? = null

    init {
        require(queueUrl.isNotBlank()) {
            "IdWorker: email module is enabled but SQS_QUEUE_URL_EMAIL is not set"
        }
    }

    override fun start() {
        val sender = ResendEmailSender(
            apiKey = env.RESEND_API_KEY,
            senderName = env.RESEND_SENDER.first,
            senderEmail = env.RESEND_SENDER.second,
        )
        val templates = TemplateRenderer()
        val baseUrl = env.ID_SERVER_BASE_URL

        val magicLink = MagicLinkEmailHandler(sender, templates, baseUrl)
        val emailChange = EmailChangeEmailHandler(sender, templates, baseUrl)
        val invitation = InvitationEmailHandler(sender, templates, baseUrl)
        val credentialStuffing = CredentialStuffingEmailHandler(sender, templates, baseUrl)

        val worker = EmailWorker(
            magicLink = magicLink,
            emailChange = emailChange,
            invitation = invitation,
            credentialStuffing = credentialStuffing,
        )

        val listener = SqsEventListener(
            queueUrl = queueUrl,
            eventTypes = worker.eventTypes,
            handler = worker::handle,
            client = SqsClientFactory.create(env),
        )
        this.listener = listener

        log.info("IdWorker: email module polling $queueUrl")
        listener.start()
    }

    override fun stop() {
        log.info("IdWorker: stopping email module")
        listener?.stop()
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailModule::class.java)
    }
}
