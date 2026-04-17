package app.domain.services.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.ITOTPConfiguration
import app.domain.repositories.IRepositoryRelatedToAccount
import java.time.Instant

data class TOTPVerificationResult(
    val valid: Boolean,
    val period: Long
)

interface ITOTPService<T : ITOTPConfiguration> : IRepositoryRelatedToAccount<T?> {
    fun enable(account: IAccount): T
    fun disable(account: IAccount): ITOTPConfiguration

    fun generate(config: T, period: Long): Int

    fun verify(config: T, period: Long, code: Int): TOTPVerificationResult
    fun verify(config: T, code: Int): TOTPVerificationResult

    fun period(config: T, instant: Instant): Long
}