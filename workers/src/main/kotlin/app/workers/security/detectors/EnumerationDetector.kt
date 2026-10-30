package app.workers.security.detectors

import com.jasminesoftwaresolutions.id.domain.events.AuthenticationFlowStartedEvent
import com.jasminesoftwaresolutions.id.domain.events.EnumerationDetectedSecurityEvent
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
 * Detects email enumeration by tracking authentication flow initiation
 * rates per IP address.
 */
class EnumerationDetector(
    private val eventDispatcher: IEventDispatcher,
    private val enforcer: RestrictionEnforcer,
    private val clientId: UUID,
) {
    private val log = LoggerFactory.getLogger(EnumerationDetector::class.java)

    companion object {
        const val FLOW_THRESHOLD = 20
        const val WINDOW_MINUTES = 5
    }

    private val flowStartsByIp = ConcurrentHashMap<String, MutableList<Instant>>()

    fun handleFlowStarted(event: AuthenticationFlowStartedEvent) {
        val ip = event.ipAddress ?: return
        val timestamps = flowStartsByIp.getOrPut(ip) { mutableListOf() }

        synchronized(timestamps) {
            val now = Instant.now()
            val cutoff = now.minus(Duration.ofMinutes(WINDOW_MINUTES.toLong()))
            timestamps.removeAll { it.isBefore(cutoff) }
            timestamps.add(now)

            if (timestamps.size >= FLOW_THRESHOLD) {
                log.warn("Enumeration detected: ip=$ip flowStarts=${timestamps.size}")

                val securityEventId = UUID.randomUUID()
                eventDispatcher.dispatch(
                    EnumerationDetectedSecurityEvent(
                        principalId = clientId,
                        action = "${IdActions.SERVICE}:EnumerationDetected",
                        resource = IdResources.platform().toString(),
                        timestamp = now,
                        eventId = securityEventId,
                        ipAddress = ip,
                        flowCount = timestamps.size,
                        windowMinutes = WINDOW_MINUTES,
                    )
                )

                enforcer.placeIpBlock(ip, securityEventId, WINDOW_MINUTES * 6)
                timestamps.clear()
            }
        }
    }
}
