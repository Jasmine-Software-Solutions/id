package app.workers

import app.workers.infrastructure.WorkerModuleName
import com.jasminesoftwaresolutions.id.domain.events.IWorker

/**
 * One enabled capability of the id-worker process.
 *
 * [start] blocks — the orchestrator runs each module on its own thread.
 */
interface WorkerModule : IWorker {
    val name: WorkerModuleName

    /** The SQS queue this module consumes. Surfaced for startup logging. */
    val queueUrl: String
}
