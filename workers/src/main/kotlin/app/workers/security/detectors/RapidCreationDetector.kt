package app.workers.security.detectors

import com.jasminesoftwaresolutions.id.domain.events.AccountCreatedEvent
import com.jasminesoftwaresolutions.id.domain.events.RapidAccountCreationDetectedSecurityEvent
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import app.workers.security.RestrictionEnforcer
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects rapid account creation from a single IP address.
 */
class RapidCreationDetector(
    private val eventDispatcher: IEventDispatcher,
    private val enforcer: RestrictionEnforcer,
    private val clientId: UUID,
    private val creationThreshold: Int = CREATION_THRESHOLD,
    private val windowMinutes: Int = WINDOW_MINUTES,
) {
    private val log = LoggerFactory.getLogger(RapidCreationDetector::class.java)

    companion object {
        const val CREATION_THRESHOLD = 10
        const val WINDOW_MINUTES = 60
    }

    private val creationsByIp = ConcurrentHashMap<String, MutableList<Instant>>()

    fun handleAccountCreated(event: AccountCreatedEvent) {
        // Every creation counts, whoever is behind it — including self-service
        // sign-ups, where the account is its own principal. An address minting a
        // run of accounts is the shape being policed, and it reads the same
        // whether the accounts were scripted by an operator or signed up one
        // after another; exempting the second kind left the detector watching
        // the rarer of the two. Applicants sharing a NAT are the cost of that,
        // which is why the threshold is generous and configurable rather than
        // fixed at the old three.
        val ip = event.ipAddress ?: return
        val timestamps = creationsByIp.getOrPut(ip) { mutableListOf() }

        synchronized(timestamps) {
            val now = Instant.now()
            val cutoff = now.minus(Duration.ofMinutes(windowMinutes.toLong()))
            timestamps.removeAll { it.isBefore(cutoff) }
            timestamps.add(now)

            if (timestamps.size >= creationThreshold) {
                log.warn("Rapid account creation detected: ip=$ip creations=${timestamps.size}")

                val securityEventId = UUID.randomUUID()
                eventDispatcher.dispatch(
                    RapidAccountCreationDetectedSecurityEvent(
                        principalId = clientId,
                        action = "${IdActions.SERVICE}:RapidAccountCreationDetected",
                        resource = IdResources.platform().toString(),
                        timestamp = now,
                        eventId = securityEventId,
                        ipAddress = ip,
                        creationCount = timestamps.size,
                        windowMinutes = windowMinutes,
                    )
                )

                enforcer.placeIpBlock(ip, securityEventId, windowMinutes)
                timestamps.clear()
            }
        }
    }
}
