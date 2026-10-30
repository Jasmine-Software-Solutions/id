package app.workers

import app.workers.email.EmailModule
import app.workers.email.EmailWorkerEnv
import app.workers.infrastructure.WorkerModuleName
import app.workers.security.SecurityModule
import app.workers.security.SecurityWorkerEnv
import app.workers.witchwood.WitchwoodModule
import app.workers.witchwood.WitchwoodWorkerEnv

/**
 * Builds the [WorkerModule]s the id-worker process should run.
 *
 * Modules are returned in [WorkerModuleName] declaration order regardless of
 * how the selection was spelled, so start order is stable.
 */
object WorkerModuleRegistry {
    fun create(
        selected: Set<WorkerModuleName>,
        securityEnv: SecurityWorkerEnv = SecurityWorkerEnv.Default,
        emailEnv: EmailWorkerEnv = EmailWorkerEnv.Default,
        witchwoodEnv: WitchwoodWorkerEnv = WitchwoodWorkerEnv.Default,
    ): List<WorkerModule> = WorkerModuleName.entries
        .filter { it in selected }
        .map { name ->
            when (name) {
                WorkerModuleName.SECURITY -> SecurityModule(securityEnv)
                WorkerModuleName.EMAIL -> EmailModule(emailEnv)
                WorkerModuleName.WITCHWOOD -> WitchwoodModule(witchwoodEnv)
            }
        }
}
