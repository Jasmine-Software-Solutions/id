package app.domain.models.account

import app.domain.models.ICreated
import app.domain.models.IIdentified

interface IAccount : IIdentified, ICreated {
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