package app.infrastructure.services

import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import com.jasminesoftwaresolutions.id.domain.events.RestrictionLiftedEvent
import com.jasminesoftwaresolutions.id.domain.events.RestrictionPlacedEvent
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.restriction.IRestriction
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionTargetType
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionType
import com.jasminesoftwaresolutions.id.domain.repositories.IRestrictionRepository
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import com.jasminesoftwaresolutions.id.domain.services.security.IRestrictionService
import java.time.Instant
import java.util.UUID

class StandardRestrictionService(
    private val repository: IRestrictionRepository,
    private val eventDispatcher: IEventDispatcher,
) : IRestrictionService {

    override fun isAgentRestricted(ip: String): Boolean =
        repository.findActive(RestrictionTargetType.IP, ip).isNotEmpty()

    override fun isAccountRestricted(account: IAccount): Boolean =
        repository.findActive(RestrictionTargetType.ACCOUNT, account.id.toString()).isNotEmpty()

    override fun place(
        type: RestrictionType,
        targetType: RestrictionTargetType,
        targetId: String,
        eventId: UUID?,
        durationMinutes: Int?,
    ): IRestriction {
        val now = Instant.now()

        val restriction = repository.create {
            this.type = type
            this.targetType = targetType
            this.targetId = targetId
            this.eventId = eventId
            this.durationMinutes = durationMinutes
            this.expiresAt = durationMinutes?.let { now.plusSeconds(it * 60L) }
        }

        eventDispatcher.dispatch(
            RestrictionPlacedEvent(
                principalId = UUID(0, 0),
                action = IdActions.PLACE_RESTRICTION,
                resource = IdResources.platform().toString(),
                timestamp = now,
                restrictionId = restriction.id,
                triggerEventId = eventId,
                type = type.name,
                targetType = targetType.name,
                targetId = targetId,
                durationMinutes = durationMinutes,
            )
        )

        return restriction
    }

    override fun lift(restrictionId: UUID) {
        val restriction = repository.findById(restrictionId) ?: return

        repository.update(restriction) {
            expiresAt = Instant.now()
        }

        eventDispatcher.dispatch(
            RestrictionLiftedEvent(
                principalId = UUID(0, 0),
                action = IdActions.LIFT_RESTRICTION,
                resource = IdResources.platform().toString(),
                timestamp = Instant.now(),
                restrictionId = restrictionId,
                type = restriction.type.name,
                targetType = restriction.targetType.name,
                targetId = restriction.targetId,
            )
        )
    }

    override fun findActive(targetType: RestrictionTargetType, targetId: String): List<IRestriction> =
        repository.findActive(targetType, targetId)

    override fun findAllActive(): List<IRestriction> =
        repository.findAllActive()
}
