package app.infrastructure.handoffs

import com.jasminesoftwaresolutions.id.domain.events.SessionRevokedEvent
import com.jasminesoftwaresolutions.id.domain.models.handoff.HandoffStatus
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffConflictException
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffExpiredException
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffNotFoundException
import com.jasminesoftwaresolutions.id.domain.services.handoff.HandoffScanStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The handoff state machine against real SQLite tables: one shared handoff per
 * original session, an independent QR row per instance, a single pending scan,
 * and the +1 session lifecycle.
 */
class HandoffStateMachineTest {

    @Test
    fun `create-or-attach shares one handoff and issues an independent QR per instance`() {
        val fixture = HandoffTestFixture()

        val first = fixture.createHandoff()
        val second = fixture.createHandoff()

        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.handoff.id, second.handoff.id)
        assertEquals(HandoffStatus.AWAITING_SCAN, second.handoff.status)

        assertNotNull(first.qr)
        assertNotNull(second.qr)
        assertNotEquals(first.qr!!.token, second.qr!!.token)

        // Both displayed codes remain valid until their own expiry.
        assertNotNull(fixture.qrTokenRepository.findValid(first.handoff.id, first.qr!!.token))
        assertNotNull(fixture.qrTokenRepository.findValid(first.handoff.id, second.qr!!.token))
    }

    @Test
    fun `only the first of two scans wins`() {
        val fixture = HandoffTestFixture()
        val created = fixture.createHandoff()
        val firstQr = created.qr!!.token

        val secondQr = fixture.service
            .issueQr(fixture.handoffRepository.findById(created.handoff.id)!!)
            .token

        fixture.scan(created.handoff.id, firstQr)

        assertThrows(HandoffConflictException::class.java) {
            fixture.scan(created.handoff.id, secondQr)
        }

        assertEquals(
            HandoffStatus.PENDING_CONFIRMATION,
            fixture.handoffRepository.findById(created.handoff.id)!!.status,
        )
    }

    @Test
    fun `reject deletes the QR rows, keeps the scan token, and reports rejected`() {
        val fixture = HandoffTestFixture()
        val created = fixture.createHandoff()
        val qr = created.qr!!
        val scan = fixture.scan(created.handoff.id, qr.token)

        val handoff = fixture.handoffRepository.findById(created.handoff.id)!!
        val fresh = fixture.service.reject(handoff)

        assertNull(fixture.qrTokenRepository.findValid(created.handoff.id, qr.token))
        assertNotNull(fixture.qrTokenRepository.findValid(created.handoff.id, fresh.token))

        val after = fixture.handoffRepository.findById(created.handoff.id)!!
        assertEquals(HandoffStatus.AWAITING_SCAN, after.status)
        assertEquals(HandoffScanStatus.REJECTED, fixture.service.scanStatus(after, scan.token))
    }

    @Test
    fun `confirm then claim creates exactly one session and a handoff-bound delegated session`() {
        val fixture = HandoffTestFixture()
        val created = fixture.createHandoff()
        fixture.scan(created.handoff.id, created.qr!!.token)

        fixture.service.confirm(fixture.handoffRepository.findById(created.handoff.id)!!)

        val claim = fixture.service.claim(
            fixture.handoffRepository.findById(created.handoff.id)!!,
            "claim-ua",
            "198.51.100.9",
        )

        assertEquals("claim-ua", claim.session.userAgent)
        assertEquals("198.51.100.9", claim.session.ipAddress)
        assertEquals(2, fixture.sessionRepository.findByAccount(fixture.account).size)
        assertEquals(created.handoff.id, claim.delegatedSession.handoffId)

        // The exchange path re-reads the delegated session by id; verify there.
        val persistedDelegated = fixture.delegatedSessionRepository.findById(claim.delegatedSession.id)!!
        assertTrue(persistedDelegated.code.verify(claim.code))
        assertEquals(created.handoff.id, persistedDelegated.handoffId)

        val after = fixture.handoffRepository.findById(created.handoff.id)!!
        assertEquals(HandoffStatus.CLAIMED, after.status)
        assertEquals(claim.session.id, after.newSessionId)

        // A second claim can never create a second session.
        assertThrows(HandoffConflictException::class.java) {
            fixture.service.claim(after, "other-ua", "198.51.100.10")
        }
        assertEquals(2, fixture.sessionRepository.findByAccount(fixture.account).size)
    }

    @Test
    fun `change devices deletes the plus-one session and returns to a fresh QR`() {
        val fixture = HandoffTestFixture()
        val created = fixture.createHandoff()
        fixture.scan(created.handoff.id, created.qr!!.token)
        fixture.service.confirm(fixture.handoffRepository.findById(created.handoff.id)!!)
        val claim = fixture.service.claim(
            fixture.handoffRepository.findById(created.handoff.id)!!,
            "claim-ua",
            "198.51.100.9",
        )

        val fresh = fixture.service.changeDevices(fixture.handoffRepository.findById(created.handoff.id)!!)

        assertNull(fixture.sessionRepository.findById(claim.session.id))
        assertNull(fixture.delegatedSessionRepository.findById(claim.delegatedSession.id))
        assertNotNull(fixture.qrTokenRepository.findValid(created.handoff.id, fresh.token))

        val after = fixture.handoffRepository.findById(created.handoff.id)!!
        assertEquals(HandoffStatus.AWAITING_SCAN, after.status)
        assertNull(after.newSessionId)
        assertEquals(1, fixture.sessionRepository.findByAccount(fixture.account).size)
    }

    @Test
    fun `the handoff expires with the original session, not on a fixed window`() {
        val sessionExpiry = Instant.now().plus(15, ChronoUnit.MINUTES)
        val fixture = HandoffTestFixture(sessionExpiresAt = sessionExpiry)

        val created = fixture.createHandoff()

        assertEquals(sessionExpiry.toEpochMilli(), created.handoff.expiresAt.toEpochMilli())
        // QRs rotate within the handoff but never outlive it.
        assertTrue(created.qr!!.expiresAt <= sessionExpiry)
    }

    @Test
    fun `an expired original session cannot start a handoff`() {
        val fixture = HandoffTestFixture(sessionExpiresAt = Instant.now().minusSeconds(1))

        assertThrows(HandoffExpiredException::class.java) {
            fixture.createHandoff()
        }
    }

    @Test
    fun `a new embed load supersedes a completed handoff and revokes its plus-one at the next confirm`() {
        val fixture = HandoffTestFixture()

        // Handoff A is claimed by an old +1 session.
        val first = fixture.createHandoff()
        val firstScan = fixture.scan(first.handoff.id, first.qr!!.token)
        fixture.service.confirm(fixture.handoffRepository.findById(first.handoff.id)!!)
        val firstClaim = fixture.service.claim(
            fixture.handoffRepository.findById(first.handoff.id)!!,
            "first-ua",
            "198.51.100.9",
        )

        // A new embed load replaces A; A's +1 session stays active for now.
        val second = fixture.createHandoff()
        assertTrue(second.created)
        assertNotEquals(first.handoff.id, second.handoff.id)

        val superseded = fixture.handoffRepository.findById(first.handoff.id)!!
        assertEquals(HandoffStatus.SUPERSEDED, superseded.status)
        assertEquals(firstClaim.session.id, superseded.newSessionId)
        assertEquals(firstClaim.session.id, second.handoff.previousSessionId)
        assertNotNull(fixture.sessionRepository.findById(firstClaim.session.id))

        // A is inert: it can no longer be scanned, confirmed, claimed or rotated.
        assertEquals(HandoffScanStatus.SUPERSEDED, fixture.service.scanStatus(superseded, firstScan.token))
        assertThrows(HandoffConflictException::class.java) {
            fixture.service.issueQr(superseded)
        }
        assertThrows(HandoffConflictException::class.java) {
            fixture.service.confirm(superseded)
        }
        assertThrows(HandoffConflictException::class.java) {
            fixture.service.claim(superseded, "late-ua", "198.51.100.11")
        }

        // Confirming B is what retires the old device.
        fixture.scan(second.handoff.id, second.qr!!.token)
        fixture.service.confirm(fixture.handoffRepository.findById(second.handoff.id)!!)

        assertNull(fixture.sessionRepository.findById(firstClaim.session.id))

        val revoked = fixture.dispatcher.events.filterIsInstance<SessionRevokedEvent>()
        assertTrue(revoked.any { it.sessionId == firstClaim.session.id && !it.wasSelfRevocation })

        // B's own claim creates the new +1 session.
        val secondClaim = fixture.service.claim(
            fixture.handoffRepository.findById(second.handoff.id)!!,
            "second-ua",
            "198.51.100.12",
        )
        assertNotNull(fixture.sessionRepository.findById(secondClaim.session.id))
        assertEquals(2, fixture.sessionRepository.findByAccount(fixture.account).size)
    }

    @Test
    fun `confirm is idempotent across instances`() {
        val fixture = HandoffTestFixture()
        val created = fixture.createHandoff()
        fixture.scan(created.handoff.id, created.qr!!.token)

        fixture.service.confirm(fixture.handoffRepository.findById(created.handoff.id)!!)
        val again = fixture.service.confirm(fixture.handoffRepository.findById(created.handoff.id)!!)

        assertEquals(HandoffStatus.CONFIRMED, again.status)
        assertEquals(0, fixture.dispatcher.events.filterIsInstance<SessionRevokedEvent>().size)
    }

    @Test
    fun `an unknown QR token is rejected`() {
        val fixture = HandoffTestFixture()
        val created = fixture.createHandoff()

        assertThrows(HandoffNotFoundException::class.java) {
            fixture.scan(created.handoff.id, "not-the-token")
        }
    }
}
