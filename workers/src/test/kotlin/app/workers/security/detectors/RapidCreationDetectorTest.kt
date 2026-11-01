package app.workers.security.detectors

import app.workers.security.RestrictionEnforcer
import com.jasminesoftwaresolutions.id.domain.events.AccountCreatedEvent
import com.jasminesoftwaresolutions.id.domain.events.Event
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.RapidAccountCreationDetectedSecurityEvent
import com.jasminesoftwaresolutions.id.domain.events.RestrictionPlacedEvent
import com.jasminesoftwaresolutions.id.domain.models.restriction.IRestriction
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionTargetType
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionType
import com.jasminesoftwaresolutions.id.domain.repositories.IRestrictionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * What the rapid-creation detector counts.
 *
 * The rule it enforces — an IP block — is enforced on every request to the ID
 * server, so a false positive is not a log line: it is a whole office, or a
 * whole family behind one NAT, unable to sign in. That cost is accepted, and
 * deliberately not softened by exempting any *kind* of creation: one address
 * minting a run of accounts is the shape being policed, and it reads the same
 * whether the accounts were scripted or signed up one after another. Only the
 * threshold stands between a busy NAT and a block, which is why it is
 * configurable and set well above the old default of three.
 *
 * The enforcer is real here rather than mocked: "one restriction row per IP"
 * is what the dev-stack check reads, and a stub would let the detector dispatch
 * an event while nothing was placed.
 */
class RapidCreationDetectorTest {

    private val clientId: UUID = UUID.randomUUID()

    private class FakeRestriction(
        override val id: UUID,
        override var type: RestrictionType,
        override var targetType: RestrictionTargetType,
        override var targetId: String,
    ) : IRestriction {
        override val createdAt: Instant = Instant.now()
        override var eventId: UUID? = null
        override var durationMinutes: Int? = null
        override var expiresAt: Instant? = null
    }

    private class FakeRestrictionRepository : IRestrictionRepository {
        val rows = mutableListOf<IRestriction>()

        override fun findActive(targetType: RestrictionTargetType, targetId: String): List<IRestriction> =
            rows.filter { it.targetType == targetType && it.targetId == targetId }

        override fun findAllActive(): List<IRestriction> = rows

        override fun findById(id: UUID): IRestriction? = rows.firstOrNull { it.id == id }

        override fun create(function: IRestriction.() -> Unit): IRestriction =
            FakeRestriction(UUID.randomUUID(), RestrictionType.IP_BLOCK, RestrictionTargetType.IP, "").also {
                it.function()
                rows += it
            }

        override fun create(id: UUID, function: IRestriction.() -> Unit): IRestriction =
            FakeRestriction(id, RestrictionType.IP_BLOCK, RestrictionTargetType.IP, "").also {
                it.function()
                rows += it
            }

        override fun update(entity: IRestriction, function: IRestriction.() -> Unit) = entity.function()
        override fun delete(entity: IRestriction) { rows -= entity }
        override fun install() = Unit
    }

    private class RecordingDispatcher : IEventDispatcher {
        val events = mutableListOf<Event>()
        override fun dispatch(event: Event) { events += event }
    }

    private val restrictions = FakeRestrictionRepository()
    private val events = RecordingDispatcher()
    private val enforcer = RestrictionEnforcer(restrictions, events, clientId)

    private val threshold = 3
    private val windowMinutes = 60

    private val detector = RapidCreationDetector(
        eventDispatcher = events,
        enforcer = enforcer,
        clientId = clientId,
        creationThreshold = threshold,
        windowMinutes = windowMinutes,
    )

    /** A creation attributed to [principal]: itself for a sign-up, an admin otherwise. */
    private fun creation(
        accountId: UUID = UUID.randomUUID(),
        principalId: UUID = accountId,
        ipAddress: String? = "203.0.113.7",
    ) = AccountCreatedEvent(
        principalId = principalId,
        action = "id.jasmine.software:CreateAccount",
        resource = "account:$accountId",
        timestamp = Instant.now(),
        accountId = accountId,
        email = "applicant@example.com",
        firstName = "",
        lastName = "",
        emailVerified = false,
        ipAddress = ipAddress,
    )

    private fun detections() = events.events.filterIsInstance<RapidAccountCreationDetectedSecurityEvent>()

    @Test
    fun `self-service sign-ups are counted and block at the threshold`() {
        // A magic-link sign-up is its own principal — nobody acted on its
        // behalf — and that is deliberately *not* an exemption. An address
        // minting a run of accounts is what the detector polices, and a run of
        // sign-ups is that shape.
        repeat(threshold - 1) { detector.handleAccountCreated(creation()) }

        assertTrue(restrictions.rows.isEmpty(), "under the threshold nothing is placed")

        detector.handleAccountCreated(creation())

        assertEquals("203.0.113.7", restrictions.rows.single().targetId)
        assertEquals(threshold, detections().single().creationCount)
    }

    @Test
    fun `operator-driven creations block at the threshold`() {
        // The threat the detector was written for: someone scripting accounts
        // from one address. The admin is the principal, the accounts are not —
        // and these count for the same reason as everything else.
        val admin = UUID.randomUUID()
        repeat(threshold - 1) { detector.handleAccountCreated(creation(principalId = admin)) }

        assertTrue(restrictions.rows.isEmpty(), "under the threshold nothing is placed")

        detector.handleAccountCreated(creation(principalId = admin))

        val restriction = restrictions.rows.single()
        assertEquals(RestrictionType.IP_BLOCK, restriction.type)
        assertEquals(RestrictionTargetType.IP, restriction.targetType)
        assertEquals("203.0.113.7", restriction.targetId)
        assertEquals(windowMinutes, restriction.durationMinutes)

        val detection = detections().single()
        assertEquals(threshold, detection.creationCount)
        assertEquals(windowMinutes, detection.windowMinutes)
        assertEquals("203.0.113.7", detection.ipAddress)
        assertEquals(clientId, detection.principalId)
    }

    @Test
    fun `a block resets the count rather than re-firing on every later creation`() {
        val admin = UUID.randomUUID()
        repeat(threshold) { detector.handleAccountCreated(creation(principalId = admin)) }

        detector.handleAccountCreated(creation(principalId = admin))

        // One block, then silence until the window fills again — a detector that
        // kept firing would place a restriction per creation.
        assertEquals(1, restrictions.rows.size)
        assertEquals(1, detections().size)
    }

    @Test
    fun `a creation with no address is not counted at all`() {
        val admin = UUID.randomUUID()
        repeat(threshold * 2) { detector.handleAccountCreated(creation(principalId = admin, ipAddress = null)) }

        // There is no address to block, and counting these against a key of
        // `null` would let unrelated creations block whoever came next.
        assertTrue(restrictions.rows.isEmpty())
        assertTrue(detections().isEmpty())
    }

    @Test
    fun `each address is counted on its own`() {
        val admin = UUID.randomUUID()
        repeat(threshold - 1) { detector.handleAccountCreated(creation(principalId = admin, ipAddress = "203.0.113.7")) }
        repeat(threshold - 1) { detector.handleAccountCreated(creation(principalId = admin, ipAddress = "198.51.100.4")) }

        // Neither address reached the threshold on its own, so neither is
        // blocked — the count is per address, not global.
        assertTrue(restrictions.rows.isEmpty(), "${restrictions.rows}")
    }

    @Test
    fun `the placed restriction is reported as a placement event too`() {
        val admin = UUID.randomUUID()
        repeat(threshold) { detector.handleAccountCreated(creation(principalId = admin)) }

        val placed = events.events.filterIsInstance<RestrictionPlacedEvent>().single()
        assertEquals(RestrictionType.IP_BLOCK.name, placed.type)
        assertEquals(RestrictionTargetType.IP.name, placed.targetType)
        assertEquals("203.0.113.7", placed.targetId)
        assertEquals(restrictions.rows.single().id, placed.restrictionId)
    }
}
