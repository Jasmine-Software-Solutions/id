package app.infrastructure.handoffs

import com.jasminesoftwaresolutions.id.domain.events.SessionCreatedEvent
import com.jasminesoftwaresolutions.id.domain.models.handoff.HandoffStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HandoffServiceTest {

    @Test
    fun `claim creates the new session with the scanning request metadata`() {
        val fixture = HandoffTestFixture()
        val created = fixture.createHandoff()
        fixture.scan(created.handoff.id, created.qr!!.token)
        fixture.service.confirm(fixture.handoffRepository.findById(created.handoff.id)!!)

        val claim = fixture.service.claim(
            fixture.handoffRepository.findById(created.handoff.id)!!,
            "scanning-ua",
            "203.0.113.7",
        )

        val persisted = fixture.sessionRepository.findById(claim.session.id)!!
        assertEquals("scanning-ua", persisted.userAgent)
        assertEquals("203.0.113.7", persisted.ipAddress)
        assertEquals(fixture.account.id, persisted.account.id)

        val events = fixture.dispatcher.events.filterIsInstance<SessionCreatedEvent>()
        assertEquals(1, events.size)
        assertEquals(claim.session.id, events.single().sessionId)
        assertEquals(fixture.account.id, events.single().accountId)
        assertEquals("scanning-ua", events.single().userAgent)
        assertEquals("203.0.113.7", events.single().ipAddress)
    }

    @Test
    fun `concurrent create-or-attach calls produce one active handoff`() {
        val fixture = HandoffTestFixture()

        val outcomes = runConcurrently { fixture.createHandoff() }

        assertTrue(outcomes.all { it.isSuccess }, outcomes.toString())
        val creations = outcomes.mapNotNull { it.getOrNull() }
        assertEquals(2, creations.size)
        assertEquals(1, creations.count { it.created })
        assertEquals(1, creations.map { it.handoff.id }.toSet().size)
        assertEquals(
            HandoffStatus.AWAITING_SCAN,
            fixture.handoffRepository.findActiveForSession(fixture.session.id)!!.status,
        )
    }

    @Test
    fun `concurrent claims produce one session`() {
        val fixture = HandoffTestFixture()
        val created = fixture.createHandoff()
        fixture.scan(created.handoff.id, created.qr!!.token)
        fixture.service.confirm(fixture.handoffRepository.findById(created.handoff.id)!!)

        val outcomes = runConcurrently {
            fixture.service.claim(
                fixture.handoffRepository.findById(created.handoff.id)!!,
                "claim-ua",
                "203.0.113.7",
            )
        }

        assertEquals(1, outcomes.count { it.isSuccess }, outcomes.toString())
        assertEquals(2, fixture.sessionRepository.findByAccount(fixture.account).size)

        val after = fixture.handoffRepository.findById(created.handoff.id)!!
        assertEquals(HandoffStatus.CLAIMED, after.status)
        assertNotNull(after.newSessionId)

        val winner = outcomes.first { it.isSuccess }.getOrThrow()
        assertEquals(winner.session.id, after.newSessionId)
        assertEquals(
            1,
            fixture.dispatcher.events.filterIsInstance<SessionCreatedEvent>().size,
        )
    }

    private fun <T> runConcurrently(block: () -> T): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)

        try {
            val futures = (1..2).map {
                executor.submit<Result<T>> {
                    gate.await()
                    runCatching(block)
                }
            }

            gate.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }
}
