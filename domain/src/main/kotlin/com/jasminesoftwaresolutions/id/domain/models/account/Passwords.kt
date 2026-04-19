package com.jasminesoftwaresolutions.id.domain.models.account

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified

interface IPassword : IIdentified,
    ICreated {
    var account: IAccount

    fun verify(password: String): Boolean
}

interface IHashedPassword : IPassword {
    var password: String
}