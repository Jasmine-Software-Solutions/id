package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.restriction.IRestriction
import com.jasminesoftwaresolutions.id.domain.models.restriction.RestrictionTargetType
import java.util.UUID

interface IRestrictionRepository : IIdentifiedRepository<IRestriction> {
    fun findActive(targetType: RestrictionTargetType, targetId: String): List<IRestriction>
    fun findAllActive(): List<IRestriction>
}
