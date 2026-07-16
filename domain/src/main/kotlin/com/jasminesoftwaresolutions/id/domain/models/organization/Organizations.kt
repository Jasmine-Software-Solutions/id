package com.jasminesoftwaresolutions.id.domain.models.organization

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.tenant.ITenant

interface IOrganization : IIdentified, ICreated {
    var tenant: ITenant
    var name: String
}
