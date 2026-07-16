package com.jasminesoftwaresolutions.id.domain.models.organization

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified

interface ITeam : IIdentified, ICreated {
    var organization: IOrganization
    var name: String
}
