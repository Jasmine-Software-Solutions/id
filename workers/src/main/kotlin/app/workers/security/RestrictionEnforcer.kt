package app.workers.security

import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.RestrictionPlacedEvent
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionTargetType
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionType
import com.jasminesoftwaresolutions.id.domain.repositories.IRestrictionRepository
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Places restrictions directly in the database and emits events.
 *
 * Previously this called the ID server's HTTP API. Now it writes
 * directly to the shared database via [IRestrictionRepository] and
 * dispatches [RestrictionPlacedEvent] for downstream consumers.
 */
class RestrictionEnforcer(
    private val restrictionRepository: IRestrictionRepository,
    private val eventDispatcher: IEventDispatcher,
    private val clientId: UUID,
) {
    private val log = LoggerFactory.getLogger(RestrictionEnforcer::class.java)

    fun placeAccountLock(accountId: String, triggerEventId: UUID, durationMinutes: Int) {
        try {
            val restriction = restrictionRepository.create {
                this.type = RestrictionType.ACCOUNT_LOCK
                this.targetType = RestrictionTargetType.ACCOUNT
                this.targetId = accountId
                this.eventId = triggerEventId
                this.durationMinutes = durationMinutes
                this.expiresAt = Instant.now().plusSeconds(durationMinutes * 60L)
            }

            eventDispatcher.dispatch(
                RestrictionPlacedEvent(
                    principalId = clientId,
                    action = "${IdActions.SERVICE}:RestrictionPlaced",
                    resource = IdResources.platform().toString(),
                    timestamp = Instant.now(),
                    restrictionId = restriction.id,
                    triggerEventId = triggerEventId,
                    type = RestrictionType.ACCOUNT_LOCK.name,
                    targetType = RestrictionTargetType.ACCOUNT.name,
                    targetId = accountId,
                    durationMinutes = durationMinutes,
                )
            )

            log.info("Placed account_lock on $accountId for ${durationMinutes}min (event: $triggerEventId)")
        } catch (e: Exception) {
            log.error("Failed to place account_lock for $accountId: ${e.message}", e)
        }
    }

    fun placeIpBlock(ip: String, triggerEventId: UUID, durationMinutes: Int) {
        try {
            val restriction = restrictionRepository.create {
                this.type = RestrictionType.IP_BLOCK
                this.targetType = RestrictionTargetType.IP
                this.targetId = ip
                this.eventId = triggerEventId
                this.durationMinutes = durationMinutes
                this.expiresAt = Instant.now().plusSeconds(durationMinutes * 60L)
            }

            eventDispatcher.dispatch(
                RestrictionPlacedEvent(
                    principalId = clientId,
                    action = "${IdActions.SERVICE}:RestrictionPlaced",
                    resource = IdResources.platform().toString(),
                    timestamp = Instant.now(),
                    restrictionId = restriction.id,
                    triggerEventId = triggerEventId,
                    type = RestrictionType.IP_BLOCK.name,
                    targetType = RestrictionTargetType.IP.name,
                    targetId = ip,
                    durationMinutes = durationMinutes,
                )
            )

            log.info("Placed ip_block on $ip for ${durationMinutes}min (event: $triggerEventId)")
        } catch (e: Exception) {
            log.error("Failed to place ip_block for $ip: ${e.message}", e)
        }
    }
}
