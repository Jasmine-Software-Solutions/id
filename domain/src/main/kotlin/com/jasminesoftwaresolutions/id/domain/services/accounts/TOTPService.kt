package com.jasminesoftwaresolutions.id.domain.services.accounts

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ITOTPConfiguration
import com.jasminesoftwaresolutions.id.domain.repositories.IRepositoryRelatedToAccount
import java.time.Instant

data class TOTPVerificationResult(
    val valid: Boolean,
    val period: Long
)

interface ITOTPService<T : ITOTPConfiguration> :
    IRepositoryRelatedToAccount<T?> {
    fun enable(account: IAccount): T
    fun disable(account: IAccount): ITOTPConfiguration

    fun generate(config: T, period: Long): Int

    fun verify(config: T, period: Long, code: Int): TOTPVerificationResult
    fun verify(config: T, code: Int): TOTPVerificationResult

    fun period(config: T, instant: Instant): Long

    fun uri(config: T): String
}