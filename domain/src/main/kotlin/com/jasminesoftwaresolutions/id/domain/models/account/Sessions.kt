package com.jasminesoftwaresolutions.id.domain.models.account

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified

interface ISession : IIdentified,
    ICreated, IExpires {
    var userAgent: String?
    var ipAddress: String?

    var account: IAccount

    var token: String

    val device: String?
        get() = null

    val location: String?
        get() = null

    fun verify(token: String): Boolean
}

interface IHashedSession : ISession