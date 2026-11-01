package app.workers.witchwood

import app.workers.infrastructure.IdAwsEnv
import app.workers.infrastructure.env

/**
 * Environment configuration for the Witchwood syncer worker.
 *
 * Every environment variable this worker consumes is declared here
 * or inherited via [IdAwsEnv].
 */
interface WitchwoodWorkerEnv : IdAwsEnv {
    val SQS_QUEUE_URL_WITCHWOOD: String
    val WITCHWOOD_HOST: String
    val WITCHWOOD_PORT: Int
    val JWT_ENCODED_PUBLIC_KEY: String
    val JWT_ENCODED_PRIVATE_KEY: String

    companion object Default : WitchwoodWorkerEnv, IdAwsEnv by IdAwsEnv.Default {
        override val SQS_QUEUE_URL_WITCHWOOD: String get() = env("SQS_QUEUE_URL_WITCHWOOD") ?: ""

        override val WITCHWOOD_HOST: String get() = env("WITCHWOOD_HOST") ?: "localhost"
        override val WITCHWOOD_PORT: Int get() = env("WITCHWOOD_PORT")?.toIntOrNull() ?: 50051

        override val JWT_ENCODED_PUBLIC_KEY: String get() = env("JWT_ENCODED_PUBLIC_KEY") ?: ""
        override val JWT_ENCODED_PRIVATE_KEY: String get() = env("JWT_ENCODED_PRIVATE_KEY") ?: ""
    }
}
