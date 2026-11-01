package app.workers.email

import app.workers.infrastructure.IdAwsEnv
import app.workers.infrastructure.env

/**
 * Environment configuration for the email worker.
 *
 * Every environment variable this worker consumes is declared here
 * or inherited via [IdAwsEnv].
 */
interface EmailWorkerEnv : IdAwsEnv {
    val SQS_QUEUE_URL_EMAIL: String
    val ID_SERVER_BASE_URL: String
    val RESEND_API_KEY: String
    val RESEND_SENDER: Pair<String, String>

    companion object Default : EmailWorkerEnv, IdAwsEnv by IdAwsEnv.Default {
        override val SQS_QUEUE_URL_EMAIL: String get() = env("SQS_QUEUE_URL_EMAIL") ?: ""

        override val ID_SERVER_BASE_URL: String get() = env("ID_SERVER_BASE_URL") ?: "http://localhost:80"

        override val RESEND_API_KEY: String get() = env("RESEND_API_KEY") ?: ""

        override val RESEND_SENDER: Pair<String, String>
            get() = (env("RESEND_SENDER_NAME") ?: "") to (env("RESEND_SENDER_EMAIL") ?: "")
    }
}
