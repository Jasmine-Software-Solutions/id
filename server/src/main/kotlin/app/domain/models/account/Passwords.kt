package app.domain.models.account

import app.domain.models.ICreated
import app.domain.models.IIdentified

interface IPassword : IIdentified, ICreated {
    var account: IAccount

    fun verify(password: String): Boolean
}

interface IHashedPassword : IPassword {
    var password: String
}