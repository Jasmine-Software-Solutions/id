package com.jasminesoftwaresolutions.id.domain.models.account

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified

interface IAccount : IIdentified,
    ICreated {
    var email: String
    var firstName: String
    var lastName: String

    var platformAdministrator: Boolean
}

interface IAccountAuthenticationConfiguration {
    val totpConfiguration: ITOTPConfiguration
    val magicLinks: List<IMagicLink>
    val passwords: List<IPassword>

    fun password(): IPassword? = passwords.maxByOrNull { it.createdAt }
}