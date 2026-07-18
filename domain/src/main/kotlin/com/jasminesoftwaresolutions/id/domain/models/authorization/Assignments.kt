package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.client.IClient
import java.time.Instant

interface IRoleAssignment : IIdentified, ICreated {
    var expiresAt: Instant?

    var account: IAccount?
    var client: IClient?
    var role: IRole
}
