package app.workers.infrastructure

/**
 * Environment contract for workers that need a database connection.
 *
 * Default implementations read standard environment variable names,
 * so concrete Env objects only need to opt in via `: IdDatabaseEnv by IdDatabaseEnv.Default`.
 */
interface IdDatabaseEnv {
    val DATABASE_DRIVER: String
    val DATABASE_URL: String
    val DATABASE_USERNAME: String
    val DATABASE_PASSWORD: String
    val DATABASE_POOL_SIZE: Int

    companion object Default : IdDatabaseEnv {
        override val DATABASE_DRIVER: String get() = env("DATABASE_DRIVER") ?: "org.postgresql.Driver"
        override val DATABASE_URL: String get() = env("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/id"
        override val DATABASE_USERNAME: String get() = env("DATABASE_USERNAME") ?: ""
        override val DATABASE_PASSWORD: String get() = env("DATABASE_PASSWORD") ?: ""
        override val DATABASE_POOL_SIZE: Int get() = env("DATABASE_POOL_SIZE")?.toIntOrNull() ?: 5
    }
}
