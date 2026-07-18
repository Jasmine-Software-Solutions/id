package com.jasminesoftwaresolutions.id.domain.models.tenant

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified

interface IOrganization : IIdentified, ICreated {
    var tenant: ITenant
    var name: String
}
