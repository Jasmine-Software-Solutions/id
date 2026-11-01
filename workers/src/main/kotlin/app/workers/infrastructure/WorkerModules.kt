package app.workers.infrastructure

/** The selectable capabilities of the single id-worker process. */
enum class WorkerModuleName(val id: String) {
    SECURITY("security"),
    EMAIL("email"),
    WITCHWOOD("witchwood");

    companion object {
        /** The environment variable that selects which modules run. */
        const val ENV_VAR = "ID_WORKER_MODULES"

        /** Unset/blank/`all` → every module. Unknown names fail loudly. */
        fun parse(raw: String?): Set<WorkerModuleName> {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty() || value.equals("all", ignoreCase = true)) return entries.toSet()

            val requested = value.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val unknown = requested.toSet() - entries.map { it.id }.toSet()
            require(unknown.isEmpty()) {
                "$ENV_VAR has unknown module(s): ${unknown.sorted().joinToString(", ")}. " +
                    "Valid: ${entries.joinToString(", ") { it.id }}, or all."
            }

            val selected = entries.filter { it.id in requested }.toSet()
            // `,` parses to an empty request. Silently running nothing is the worst
            // outcome here — the process would look healthy while SQS accumulated.
            require(selected.isNotEmpty()) { "$ENV_VAR selected no modules: '$value'" }
            return selected
        }
    }
}
