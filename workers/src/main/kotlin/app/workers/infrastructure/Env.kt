package app.workers.infrastructure

/**
 * Reads an environment variable, returning `null` when it is unset.
 *
 * Every env object in this module — the [IdAwsEnv] / [IdDatabaseEnv] defaults
 * and the concrete `*WorkerEnv.Default` objects — reads the same way.
 */
internal fun env(name: String): String? = System.getenv(name)
