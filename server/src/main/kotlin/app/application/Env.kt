package app.application

import io.github.cdimascio.dotenv.dotenv
import java.util.*

object Env {
    private val dotenv = dotenv {
        filename = ".env"

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

    val RESEND_API_KEY = dotenv["RESEND_API_KEY"] ?: ""
    val RESEND_SENDER = (dotenv["RESEND_SENDER_NAME"] ?: "") to (dotenv["RESEND_SENDER_EMAIL"] ?: "")

    val ENCRYPTED_PARAMETER_SECRET = dotenv["ENCRYPTED_PARAMETER_SECRET"] ?: ""
    val ENCRYPTED_PARAMETER_SALT = dotenv["ENCRYPTED_PARAMETER_SALT"] ?: ""

    val SESSION_ACCESS_TOKEN_LIFETIME = dotenv["SESSION_ACCESS_TOKEN_LIFETIME"]?.toLongOrNull() ?: 300
    val MACHINE_ACCESS_TOKEN_LIFETIME = dotenv["MACHINE_ACCESS_TOKEN_LIFETIME"]?.toLongOrNull() ?: 300

    val EXTERNAL_BASE_URL = dotenv["EXTERNAL_BASE_URL"] ?: "http://localhost:$PORT"

    val JWT_PUBLIC_KEY = dotenv["JWT_ENCODED_PUBLIC_KEY"]?.let { Base64.getDecoder().decode(it) }
    val JWT_PRIVATE_KEY = dotenv["JWT_ENCODED_PRIVATE_KEY"]?.let { Base64.getDecoder().decode(it) }
}