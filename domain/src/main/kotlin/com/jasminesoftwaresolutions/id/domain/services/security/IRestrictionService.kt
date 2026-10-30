package com.jasminesoftwaresolutions.id.domain.services.security

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.restriction.IRestriction
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionTargetType
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionType
import java.util.UUID

interface IRestrictionService {
    fun isAgentRestricted(ip: String): Boolean
    fun isAccountRestricted(account: IAccount): Boolean

    fun place(
        type: RestrictionType,
        targetType: RestrictionTargetType,
        targetId: String,
        eventId: UUID?,
        durationMinutes: Int?,
    ): IRestriction

    fun lift(restrictionId: UUID)

    fun findActive(targetType: RestrictionTargetType, targetId: String): List<IRestriction>
    fun findAllActive(): List<IRestriction>
}
