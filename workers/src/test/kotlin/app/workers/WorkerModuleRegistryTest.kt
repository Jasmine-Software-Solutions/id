package app.workers

import app.workers.email.EmailWorkerEnv
import app.workers.infrastructure.IdAwsEnv
import app.workers.infrastructure.IdDatabaseEnv
import app.workers.infrastructure.WorkerModuleName
import app.workers.security.SecurityWorkerEnv
import app.workers.witchwood.WitchwoodWorkerEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Which modules a selection builds, and which queue each one ends up bound to.
 *
 * The three modules read three different queue variables, and the failure mode
 * if that wiring crossed over is silent: two listeners would compete on one
 * queue while a third sat unread.
 */
class WorkerModuleRegistryTest {

    @Test
    fun `only the selected modules are built`() {
        val modules = WorkerModuleRegistry.create(
            setOf(WorkerModuleName.EMAIL),
            emailEnv = emailEnv(QUEUE_EMAIL),
        )
        assertEquals(listOf(WorkerModuleName.EMAIL), modules.map { it.name })
    }

    @Test
    fun `every module runs when all are selected`() {
        val modules = WorkerModuleRegistry.create(
            WorkerModuleName.entries.toSet(),
            securityEnv = securityEnv(QUEUE_SECURITY),
            emailEnv = emailEnv(QUEUE_EMAIL),
            witchwoodEnv = witchwoodEnv(QUEUE_WITCHWOOD),
        )
        assertEquals(WorkerModuleName.entries.toList(), modules.map { it.name })
    }

    @Test
    fun `start order is fixed, not the order the selection was spelled in`() {
        val modules = WorkerModuleRegistry.create(
            setOf(WorkerModuleName.WITCHWOOD, WorkerModuleName.EMAIL),
            emailEnv = emailEnv(QUEUE_EMAIL),
            witchwoodEnv = witchwoodEnv(QUEUE_WITCHWOOD),
        )
        assertEquals(listOf(WorkerModuleName.EMAIL, WorkerModuleName.WITCHWOOD), modules.map { it.name })
    }

    @Test
    fun `each module is bound to its own queue`() {
        val modules = WorkerModuleRegistry.create(
            WorkerModuleName.entries.toSet(),
            securityEnv = securityEnv(QUEUE_SECURITY),
            emailEnv = emailEnv(QUEUE_EMAIL),
            witchwoodEnv = witchwoodEnv(QUEUE_WITCHWOOD),
        )

        assertEquals(QUEUE_SECURITY, modules.single { it.name == WorkerModuleName.SECURITY }.queueUrl)
        assertEquals(QUEUE_EMAIL, modules.single { it.name == WorkerModuleName.EMAIL }.queueUrl)
        assertEquals(QUEUE_WITCHWOOD, modules.single { it.name == WorkerModuleName.WITCHWOOD }.queueUrl)
    }

    @Test
    fun `a selected module with no queue url fails naming its variable`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            WorkerModuleRegistry.create(setOf(WorkerModuleName.WITCHWOOD), witchwoodEnv = witchwoodEnv(""))
        }
        assertTrue(failure.message.orEmpty().contains("SQS_QUEUE_URL_WITCHWOOD"), failure.message)
    }

    @Test
    fun `an unselected module needs no configuration at all`() {
        // The point of the switch: `ID_WORKER_MODULES=email` must run with no
        // database and no JWT keys, so the security module must not be built —
        // and therefore must not inspect — its (here, empty) environment.
        val modules = WorkerModuleRegistry.create(
            setOf(WorkerModuleName.EMAIL),
            securityEnv = securityEnv(""),
            witchwoodEnv = witchwoodEnv(""),
            emailEnv = emailEnv(QUEUE_EMAIL),
        )
        assertEquals(listOf(WorkerModuleName.EMAIL), modules.map { it.name })
    }

    private companion object {
        const val QUEUE_SECURITY = "http://moto:4566/000000000000/id-security"
        const val QUEUE_EMAIL = "http://moto:4566/000000000000/id-email"
        const val QUEUE_WITCHWOOD = "http://moto:4566/000000000000/id-witchwood"

        fun securityEnv(queueUrl: String): SecurityWorkerEnv = object : SecurityWorkerEnv,
            IdDatabaseEnv by IdDatabaseEnv.Default,
            IdAwsEnv by IdAwsEnv.Default {
            override val SQS_QUEUE_URL_SECURITY = queueUrl
            override val CREATION_THRESHOLD = 10
            override val CREATION_WINDOW_MINUTES = 60
            override val SNS_TOPIC_ARN = ""
            override val SNS_REGION = "us-east-1"
            override val SNS_ENDPOINT_OVERRIDE: String? = null
        }

        fun emailEnv(queueUrl: String): EmailWorkerEnv = object : EmailWorkerEnv,
            IdAwsEnv by IdAwsEnv.Default {
            override val SQS_QUEUE_URL_EMAIL = queueUrl
            override val ID_SERVER_BASE_URL = "http://id.localhost"
            override val RESEND_API_KEY = ""
            override val RESEND_SENDER = "" to ""
        }

        fun witchwoodEnv(queueUrl: String): WitchwoodWorkerEnv = object : WitchwoodWorkerEnv,
            IdAwsEnv by IdAwsEnv.Default {
            override val SQS_QUEUE_URL_WITCHWOOD = queueUrl
            override val WITCHWOOD_HOST = "localhost"
            override val WITCHWOOD_PORT = 50051
            override val JWT_ENCODED_PUBLIC_KEY = ""
            override val JWT_ENCODED_PRIVATE_KEY = ""
        }
    }
}
