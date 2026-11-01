package com.jasminesoftwaresolutions.id.domain.models.tenant

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified

interface ITenant : IIdentified,
    ICreated {
    var suspended: Boolean
    var name: String
}