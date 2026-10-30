package com.jasminesoftwaresolutions.id.domain.services.accounts

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ITOTPConfiguration
import com.jasminesoftwaresolutions.id.domain.repositories.IRepositoryRelatedToAccount
import java.time.Instant
import java.util.UUID

data class TOTPVerificationResult(
    val valid: Boolean,
    val period: Long
)

interface ITOTPService {
    fun findByAccount(account: IAccount): ITOTPConfiguration
        = findByAccount(account.id)

    fun findByAccount(id: UUID): ITOTPConfiguration

    fun enable(account: IAccount): ITOTPConfiguration
    fun disable(account: IAccount): ITOTPConfiguration

    fun generate(config: ITOTPConfiguration, period: Long): Int

    fun verify(config: ITOTPConfiguration, period: Long, code: Int): TOTPVerificationResult
    fun verify(config: ITOTPConfiguration, code: Int): TOTPVerificationResult

    fun period(config: ITOTPConfiguration, instant: Instant): Long

    fun uri(config: ITOTPConfiguration): String
}
