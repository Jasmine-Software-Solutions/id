package app.workers.entrypoints

import app.workers.WorkerModule
import app.workers.WorkerModuleRegistry
import app.workers.infrastructure.WorkerModuleName
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("app.workers.entrypoints.IdWorkerMain")

/**
 * The single ID worker process.
 *
 * Every module selected by `ID_WORKER_MODULES` gets one non-daemon thread and
 * blocks in its own `start()`; `main` stays parked in `join()` until the JVM is
 * asked to shut down.
 *
 * ```
 * java -cp workers-all.jar app.workers.entrypoints.IdWorkerMainKt
 * ```
 */
fun main() {
    val selected = orExit { WorkerModuleName.parse(System.getenv(WorkerModuleName.ENV_VAR)) }
    val modules = orExit { WorkerModuleRegistry.create(selected) }

    modules.forEach { log.info("IdWorker: enabling ${it.name.id} (queue ${it.queueUrl})") }

    val shuttingDown = AtomicBoolean(false)
    Runtime.getRuntime().addShutdownHook(Thread({
        shuttingDown.set(true)
        // Reverse of start order, and each stop is isolated so one module that
        // throws cannot strand the others.
        modules.reversed().forEach { module ->
            runCatching { module.stop() }
                .onFailure { log.error("IdWorker: ${module.name.id} module failed to stop", it) }
        }
    }, "id-worker-shutdown"))

    val threads = modules.map { module ->
        Thread({
            try {
                module.start() // blocks until stop()
            } catch (t: Throwable) {
                log.error("IdWorker: ${module.name.id} module failed", t)
            } finally {
                // A module's start() only returns once its listener has stopped. During
                // a graceful shutdown that is expected; at any other time it means a
                // queue is no longer being consumed, so take the process down and let
                // the container be restarted rather than leave it half-alive.
                if (!shuttingDown.get()) {
                    log.error("IdWorker: ${module.name.id} module stopped unexpectedly, exiting")
                    exitProcess(1)
                }
            }
        }, "id-worker-${module.name.id}")
    }

    threads.forEach { it.start() }
    threads.forEach { it.join() }
}

/**
 * Runs [block], reporting a validation failure as a single clean log line
 * instead of a stack trace. Only wraps our own `require`-style checks.
 */
private inline fun <T> orExit(block: () -> T): T =
    try {
        block()
    } catch (e: IllegalArgumentException) {
        // The messages already identify their source (the variable name, or the
        // module that is missing it); do not prefix them twice.
        log.error(e.message)
        exitProcess(1)
    }
