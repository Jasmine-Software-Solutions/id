package app.workers.infrastructure

/**
 * Environment contract for workers that need AWS SDK clients (SQS, SNS).
 *
 * Default implementations read standard environment variable names,
 * so concrete Env objects only need to opt in via `: IdAwsEnv by IdAwsEnv.Default`.
 */
interface IdAwsEnv {
    val AWS_REGION: String
    val AWS_ACCESS_KEY_ID: String
    val AWS_SECRET_ACCESS_KEY: String
    val AWS_PROFILE: String
    val SQS_ENDPOINT_OVERRIDE: String?

    companion object Default : IdAwsEnv {
        override val AWS_REGION: String get() = env("SQS_REGION") ?: env("AWS_REGION") ?: "us-east-1"
        override val AWS_ACCESS_KEY_ID: String get() = env("AWS_ACCESS_KEY_ID") ?: ""
        override val AWS_SECRET_ACCESS_KEY: String get() = env("AWS_SECRET_ACCESS_KEY") ?: ""
        override val AWS_PROFILE: String get() = env("AWS_PROFILE") ?: ""
        override val SQS_ENDPOINT_OVERRIDE: String? get() = env("SQS_ENDPOINT_OVERRIDE")
    }
}
