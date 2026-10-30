package com.jasminesoftwaresolutions.id.domain.models.tenant

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import java.time.Instant

interface IMembershipInvitation : IIdentified, ICreated {
    var email: String
    var tenant: ITenant
    var acceptedAt: Instant?
    var token: String

    fun verify(token: String): Boolean
}

interface IHashedMembershipInvitation : IMembershipInvitation
