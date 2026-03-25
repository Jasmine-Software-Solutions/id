package app

import io.github.cdimascio.dotenv.dotenv

object Env {
    val ENV_FILE_PATH = System.getenv("ENV_FILE_PATH") ?: ".env"

    private val dotenv = dotenv {
        filename = ENV_FILE_PATH

        ignoreIfMissing = true
        ignoreIfMalformed = false
    }

    val PORT = dotenv["PORT"]?.toIntOrNull() ?: 80

    val DATABASE_DRIVER = dotenv["DATABASE_DRIVER"] ?: "org.sqlite.JDBC"
    val DATABASE_URL = dotenv["DATABASE_URL"] ?: "jdbc:sqlite:file:test?mode=memory&cache=shared"
    val DATABASE_USERNAME = dotenv["DATABASE_USERNAME"] ?: ""
    val DATABASE_PASSWORD = dotenv["DATABASE_PASSWORD"] ?: ""
    val DATABASE_POOL_SIZE = dotenv["DATABASE_POOL_SIZE"]?.toIntOrNull() ?: 1

    val PASSWORD_SALT_LENGTH = dotenv["PASSWORD_SALT_LENGTH"]?.toIntOrNull() ?: 32
    val PASSWORD_HASH_ITERATIONS = dotenv["PASSWORD_HASH_ITERATIONS"]?.toIntOrNull() ?: 2
    val ARGON2_MEMORY = dotenv["ARGON2_MEMORY"]?.toIntOrNull() ?: 65535
    val ARGON2_PARALLELISM = dotenv["ARGON2_PARALLELISM"]?.toIntOrNull() ?: 1

    val HOT_RELOAD_JTE_TEMPLATES = dotenv["HOT_RELOAD_JTE_TEMPLATES"]?.toBooleanStrictOrNull() ?: false

    val SMTP_ENABLED = dotenv["SMTP_ENABLED"]?.toBooleanStrictOrNull() ?: false

    val SMTP_AUTH = dotenv["SMTP_AUTH"]?.toBooleanStrictOrNull() ?: true
    val SMTP_STARTTLS_ENABLE = dotenv["SMTP_STARTTLS_ENABLE"]?.toBooleanStrictOrNull() ?: true
    val SMTP_HOST = dotenv["SMTP_HOST"]
    val SMTP_PORT = dotenv["SMTP_PORT"]?.toIntOrNull() ?: 25
    val SMTP_SSL_TRUST = dotenv["SMTP_SSL_TRUST"]

    val SMTP_USERNAME = dotenv["SMTP_USERNAME"]
    val SMTP_PASSWORD = dotenv["SMTP_PASSWORD"]
    val SMTP_EMAIL = dotenv["SMTP_EMAIL"]

    val SUPPORT_EMAIL = dotenv["SUPPORT_EMAIL"] ?: SMTP_EMAIL

    val ENCRYPTED_PARAMETER_SECRET = dotenv["ENCRYPTED_PARAMETER_SECRET"] ?: ""
    val ENCRYPTED_PARAMETER_SALT = dotenv["ENCRYPTED_PARAMETER_SALT"] ?: ""

    val SESSION_ACCESS_TOKEN_LIFETIME = dotenv["SESSION_ACCESS_TOKEN_LIFETIME"]?.toLongOrNull() ?: 300
    val MACHINE_ACCESS_TOKEN_LIFETIME = dotenv["MACHINE_ACCESS_TOKEN_LIFETIME"]?.toLongOrNull() ?: 300

    val EXTERNAL_BASE_URL = dotenv["EXTERNAL_BASE_URL"] ?: "http://localhost:$PORT"

    object Test {
        val URL = (dotenv["TEST_URL"] ?: "http://localhost:$PORT").let {
            if (it.endsWith("/")) it.substring(0, it.length - 1) else it
        }

        val HEADLESS = dotenv["TEST_HEADLESS"]?.toBooleanStrictOrNull() ?: true
        val LOGIN_TIMEOUT = dotenv["LOGIN_TIMEOUT"]?.toDoubleOrNull() ?: 1000.0
    }
}
