package com.jasminesoftwaresolutions.id.domain.models.account

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.authorization.IPlatformRole

interface IAccount : IIdentified, ICreated {
    var email: String
    var firstName: String
    var lastName: String

    var roles: Set<IPlatformRole>
}
