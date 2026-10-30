package app.workers.security

import app.workers.infrastructure.IdAwsEnv
import app.workers.infrastructure.IdDatabaseEnv
import app.workers.infrastructure.env

/**
 * Environment configuration for the security worker.
 *
 * Every environment variable this worker consumes is declared here
 * or inherited via [IdDatabaseEnv] and [IdAwsEnv].
 */
interface SecurityWorkerEnv : IdDatabaseEnv, IdAwsEnv {
    val SQS_QUEUE_URL_SECURITY: String

    /** Account creations from one IP within [CREATION_WINDOW_MINUTES] that trip the detector. */
    val CREATION_THRESHOLD: Int

    /** Length of the window the rapid-creation detector counts over. */
    val CREATION_WINDOW_MINUTES: Int

    val SNS_TOPIC_ARN: String
    val SNS_REGION: String
    val SNS_ENDPOINT_OVERRIDE: String?

    companion object Default : SecurityWorkerEnv,
        IdDatabaseEnv by IdDatabaseEnv.Default,
        IdAwsEnv by IdAwsEnv.Default {

        override val SQS_QUEUE_URL_SECURITY: String get() = env("SQS_QUEUE_URL_SECURITY") ?: ""

        override val CREATION_THRESHOLD: Int get() = env("CREATION_THRESHOLD")?.toIntOrNull() ?: 10
        override val CREATION_WINDOW_MINUTES: Int get() = env("CREATION_WINDOW_MINUTES")?.toIntOrNull() ?: 60

        override val SNS_TOPIC_ARN: String get() = env("SNS_TOPIC_ARN") ?: ""
        override val SNS_REGION: String get() = env("SNS_REGION") ?: "us-east-1"
        override val SNS_ENDPOINT_OVERRIDE: String? get() = env("SNS_ENDPOINT_OVERRIDE")
    }
}
