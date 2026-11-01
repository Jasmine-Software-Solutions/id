package com.jasminesoftwaresolutions.id.domain.models.restriction

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import java.time.Instant
import java.util.UUID

enum class RestrictionType { ACCOUNT_LOCK, IP_BLOCK }

enum class RestrictionTargetType { ACCOUNT, IP }

interface IRestriction : IIdentified, ICreated {
    var type: RestrictionType
    var targetType: RestrictionTargetType
    var targetId: String
    var eventId: UUID?
    var durationMinutes: Int?
    var expiresAt: Instant?
}
