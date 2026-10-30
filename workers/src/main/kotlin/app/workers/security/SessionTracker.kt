package app.workers.security

import com.jasminesoftwaresolutions.id.domain.events.AllSessionsRevokedEvent
import com.jasminesoftwaresolutions.id.domain.events.ConcurrentSessionLimitExceededSecurityEvent
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.SessionCreatedEvent
import com.jasminesoftwaresolutions.id.domain.events.SessionRevokedEvent
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks active session counts per account and raises an event when
 * the maximum concurrent session limit is exceeded.
 */
class SessionTracker(
    private val eventDispatcher: IEventDispatcher,
    private val clientId: UUID,
) {
    private val log = LoggerFactory.getLogger(SessionTracker::class.java)

    companion object {
        const val MAX_CONCURRENT_SESSIONS = 10
    }

    private val activeSessionCounts = ConcurrentHashMap<UUID, Int>()

    fun handleSessionCreated(event: SessionCreatedEvent) {
        val count = activeSessionCounts.merge(event.accountId, 1, Int::plus)!!

        if (count > MAX_CONCURRENT_SESSIONS) {
            log.warn("Concurrent session limit exceeded: account=${event.accountId} sessions=$count")

            val securityEventId = UUID.randomUUID()
            eventDispatcher.dispatch(
                ConcurrentSessionLimitExceededSecurityEvent(
                    principalId = clientId,
                    action = "${IdActions.SERVICE}:ConcurrentSessionLimitExceeded",
                    resource = IdResources.platform().toString(),
                    timestamp = Instant.now(),
                    eventId = securityEventId,
                    accountId = event.accountId,
                    activeSessionCount = count,
                    limit = MAX_CONCURRENT_SESSIONS,
                )
            )
        }
    }

    fun handleSessionRevoked(event: SessionRevokedEvent) {
        activeSessionCounts.computeIfPresent(event.accountId) { _, c -> maxOf(0, c - 1) }
    }

    fun handleAllSessionsRevoked(event: AllSessionsRevokedEvent) {
        activeSessionCounts[event.accountId] = 0
    }

    fun removeAccount(accountId: UUID) {
        activeSessionCounts.remove(accountId)
    }
}
