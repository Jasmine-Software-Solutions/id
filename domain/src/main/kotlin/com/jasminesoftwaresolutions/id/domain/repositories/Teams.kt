package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.organization.IOrganization
import com.jasminesoftwaresolutions.id.domain.models.organization.ITeam
import java.util.*

interface ITeamRepository<T : ITeam> : IIdentifiedRepository<T> {
    fun findByOrganization(organization: IOrganization): List<T> = findByOrganization(organization.id)
    fun findByOrganization(id: UUID): List<T>
}