package app.domain.models.account

import java.time.Instant


interface ITOTPConfiguration {
    var account: IAccount
    var enabled: Boolean
}

interface ISetTOTPConfiguration : ITOTPConfiguration {
    var confirmedAt: Instant?

    var digits: Int
    var periodSeconds: Long
    var algorithm: String

    fun verify(code: Int): Boolean
}

interface IEncryptedTOTPConfiguration : ISetTOTPConfiguration {
    var secret: ByteArray
}