package app.domain.models.account

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import java.time.Instant
import kotlin.time.Duration


interface ITOTPConfiguration {
    var account: IAccount
    var enabled: Boolean
}

interface ISetTOTPConfiguration : ITOTPConfiguration {
    var confirmedAt: Instant?

    var digits: Int
    var period: Duration
    var algorithm: HmacAlgorithm
}

interface IEncryptedTOTPConfiguration : ISetTOTPConfiguration {
    var secret: ByteArray
}