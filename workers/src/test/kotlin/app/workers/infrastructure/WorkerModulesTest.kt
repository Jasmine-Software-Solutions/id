package app.workers.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What `ID_WORKER_MODULES` accepts, and — more importantly — what it refuses.
 *
 * The refusals carry the weight here. A worker that starts, logs nothing and
 * polls nothing looks identical to a healthy one from the outside while SQS
 * quietly accumulates, so every input that could select zero modules has to
 * fail loudly at startup instead.
 */
class WorkerModulesTest {

    private val all = WorkerModuleName.entries.toSet()

    @Test
    fun `unset selects every module`() {
        assertEquals(all, WorkerModuleName.parse(null))
    }

    @Test
    fun `blank selects every module`() {
        assertEquals(all, WorkerModuleName.parse(""))
        assertEquals(all, WorkerModuleName.parse("   "))
    }

    @Test
    fun `all selects every module, in any case`() {
        assertEquals(all, WorkerModuleName.parse("all"))
        assertEquals(all, WorkerModuleName.parse("ALL"))
        assertEquals(all, WorkerModuleName.parse(" All "))
    }

    @Test
    fun `a comma separated list selects exactly those modules`() {
        assertEquals(
            setOf(WorkerModuleName.EMAIL, WorkerModuleName.WITCHWOOD),
            WorkerModuleName.parse(" email , witchwood "),
        )
    }

    @Test
    fun `names are matched case insensitively`() {
        assertEquals(setOf(WorkerModuleName.SECURITY), WorkerModuleName.parse("SECURITY"))
    }

    @Test
    fun `a repeated name is not an error`() {
        assertEquals(setOf(WorkerModuleName.EMAIL), WorkerModuleName.parse("email,email"))
    }

    @Test
    fun `an unknown name is rejected and the valid ones are named`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            WorkerModuleName.parse("email,bogus")
        }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("bogus"), message)
        WorkerModuleName.entries.forEach { assertTrue(message.contains(it.id), message) }
    }

    @Test
    fun `a list that selects nothing is rejected`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            WorkerModuleName.parse(",")
        }
        assertTrue(failure.message.orEmpty().contains("selected no modules"), failure.message)
    }
}
