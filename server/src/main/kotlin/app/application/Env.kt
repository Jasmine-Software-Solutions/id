package app.application

import java.util.*

/**
 * Environment configuration for the ID server.
 *
 * In development, values come from a `.env` file loaded by Docker Compose.
 * In production, values come from ECS task definition (environment + secrets
 * injected from AWS Systems Manager Parameter Store).
 *
 * All required values use [requireNotNull] to fail fast at startup rather
 * than silently using dangerous defaults.
 */
object Env {
    // Read from environment or .env file. Docker Compose's `env_file` handles
    // the .env → environment variable mapping for local development.
    private fun env(name: String): String? = System.getenv(name)
    private fun envRequired(name: String): String =
        requireNotNull(System.getenv(name)) { "$name is required. Set via environment variable." }

    // -- Server -----------------------------------------------------------------

    val PORT = env("PORT")?.toIntOrNull() ?: 80

    // -- Database ---------------------------------------------------------------

    val DATABASE_DRIVER = env("DATABASE_DRIVER") ?: "org.sqlite.JDBC"
    val DATABASE_URL = envRequired("DATABASE_URL")
    val DATABASE_USERNAME = env("DATABASE_USERNAME") ?: ""
    val DATABASE_PASSWORD = envRequired("DATABASE_PASSWORD")
    val DATABASE_POOL_SIZE = env("DATABASE_POOL_SIZE")?.toIntOrNull() ?: 5

    // -- Argon2 -----------------------------------------------------------------

    val PASSWORD_SALT_LENGTH = env("PASSWORD_SALT_LENGTH")?.toIntOrNull() ?: 32
    val PASSWORD_HASH_ITERATIONS = env("PASSWORD_HASH_ITERATIONS")?.toIntOrNull() ?: 4
    val ARGON2_MEMORY = env("ARGON2_MEMORY")?.toIntOrNull() ?: 65536  // 64 MB in KB
    val ARGON2_PARALLELISM = env("ARGON2_PARALLELISM")?.toIntOrNull() ?: 1

    val DEV_ENVIRONMENT = env("DEV_ENVIRONMENT")?.toBooleanStrictOrNull() ?: false

    // -- Email (Resend) ---------------------------------------------------------

    val RESEND_API_KEY = env("RESEND_API_KEY") ?: ""
    val RESEND_SENDER = (env("RESEND_SENDER_NAME") ?: "") to (env("RESEND_SENDER_EMAIL") ?: "")

    // -- Encryption -------------------------------------------------------------

    val ENCRYPTED_PARAMETER_SECRET = envRequired("ENCRYPTED_PARAMETER_SECRET")
    val ENCRYPTED_PARAMETER_SALT = env("ENCRYPTED_PARAMETER_SALT") ?: ""

    // -- Tokens / Sessions ------------------------------------------------------

    val SESSION_ACCESS_TOKEN_LIFETIME = env("SESSION_ACCESS_TOKEN_LIFETIME")?.toLongOrNull() ?: 300
    val MACHINE_ACCESS_TOKEN_LIFETIME = env("MACHINE_ACCESS_TOKEN_LIFETIME")?.toLongOrNull() ?: 300

    // -- URLs -------------------------------------------------------------------

    val EXTERNAL_BASE_URL = env("EXTERNAL_BASE_URL") ?: "http://localhost:$PORT"

    // -- Proxy ------------------------------------------------------------------

    /**
     * The header a proxy in front of this server carries the client's address
     * in, e.g. `X-Forwarded-For`. Unset — the default — means the request's
     * socket peer is the client, which is correct when nothing sits in front.
     *
     * Its left-most value is believed as-is, so only set this behind a proxy
     * that *overwrites* the header: anyone able to reach the server directly
     * could otherwise choose the address they are judged (and blocked) by.
     */
    val CLIENT_IP_HEADER = env("CLIENT_IP_HEADER")?.trim()?.takeIf { it.isNotEmpty() }

    // -- JWT --------------------------------------------------------------------

    val JWT_PUBLIC_KEY = Base64.getDecoder().decode(envRequired("JWT_ENCODED_PUBLIC_KEY"))
    val JWT_PRIVATE_KEY = Base64.getDecoder().decode(envRequired("JWT_ENCODED_PRIVATE_KEY"))

    // -- Witchwood --------------------------------------------------------------

    val WITCHWOOD_HOST = env("WITCHWOOD_HOST") ?: "localhost"
    val WITCHWOOD_PORT = env("WITCHWOOD_PORT")?.toIntOrNull() ?: 50051

    // -- AWS / SNS --------------------------------------------------------------

    val SNS_TOPIC_ARN = env("SNS_TOPIC_ARN") ?: ""
    val SNS_REGION = env("SNS_REGION") ?: "us-east-1"
    val SNS_ENDPOINT_OVERRIDE = env("SNS_ENDPOINT_OVERRIDE")
    val AWS_PROFILE = env("AWS_PROFILE") ?: "default"
    val AWS_ACCESS_KEY_ID = env("AWS_ACCESS_KEY_ID") ?: ""
    val AWS_SECRET_ACCESS_KEY = env("AWS_SECRET_ACCESS_KEY") ?: ""
    val AWS_SESSION_TOKEN = env("AWS_SESSION_TOKEN") ?: ""

    // -- SETUP

    val INITIAL_ADMIN_EMAIL = env("INITIAL_ADMIN_EMAIL") ?: ""
    val INITIAL_ADMIN_PASSWORD = env("INITIAL_ADMIN_PASSWORD") ?: ""
}
