package com.jasminesoftwaresolutions.id.domain.models.account

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

    var secret: ByteArray
}

interface IEncryptedTOTPConfiguration : ISetTOTPConfiguration