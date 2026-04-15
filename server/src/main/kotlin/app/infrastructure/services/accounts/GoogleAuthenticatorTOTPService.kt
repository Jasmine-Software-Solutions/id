package app.infrastructure.services.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IEncryptedTOTPConfiguration
import app.domain.models.account.ITOTPConfiguration
import app.domain.repositories.ITOTPConfigurationRepository
import app.domain.services.accounts.ITOTPService
import app.domain.services.accounts.TOTPVerificationResult
import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.HmacOneTimePasswordGenerator
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GoogleAuthenticatorTOTPService(
    val totpRepository: ITOTPConfigurationRepository,
    val defaultAlgorithm: HmacAlgorithm = HmacAlgorithm.SHA1,
    val defaultPeriod: Duration = 30.seconds,
    val defaultDigits: Int = 6
) : ITOTPService<IEncryptedTOTPConfiguration> {
    override fun findByAccount(id: UUID): IEncryptedTOTPConfiguration
        = totpRepository.findByAccount(id) as IEncryptedTOTPConfiguration

    override fun enable(account: IAccount): IEncryptedTOTPConfiguration {
        val newSecret = GoogleAuthenticator.createRandomSecretAsByteArray()
        try {
            val existingConfiguration = totpRepository.findByAccount(account)
            totpRepository.update(existingConfiguration) {
                val new = this as IEncryptedTOTPConfiguration
                new.enabled = true
                new.algorithm = defaultAlgorithm
                new.period = defaultPeriod
                new.digits = defaultDigits
                new.secret = newSecret
            }

            return existingConfiguration as IEncryptedTOTPConfiguration
        } finally {
            Arrays.fill(newSecret, 0)
        }
    }

    override fun disable(account: IAccount): ITOTPConfiguration {
        val existingConfiguration = totpRepository.findByAccount(account)
        totpRepository.update(existingConfiguration) {
            this.enabled = false
        }

        return existingConfiguration
    }

    override fun generate(config: IEncryptedTOTPConfiguration, period: Long): Int {
        val totpSecret = config.secret.clone()

        try {
            val generatorConfig = TimeBasedOneTimePasswordConfig(
                config.period.inWholeSeconds,
                TimeUnit.SECONDS,
                config.digits,
                config.algorithm
            )

            val hotpGenerator = HmacOneTimePasswordGenerator(totpSecret, generatorConfig)
            val code = hotpGenerator.generate(period)

            return Integer.valueOf(code)
        } finally {
            Arrays.fill(totpSecret, 0)
        }
    }

    override fun verify(
        config: IEncryptedTOTPConfiguration,
        period: Long,
        code: Int
    ): TOTPVerificationResult {
        val codeAtPeriod = generate(config, period)
        return TOTPVerificationResult(codeAtPeriod == code, period)
    }

    override fun verify(
        config: IEncryptedTOTPConfiguration,
        code: Int
    ): TOTPVerificationResult {
        val periodNow = period(config, Instant.now())

        val codeNow = generate(config, periodNow)
        val codeEarlier = generate(config, periodNow - 1)

        if (codeEarlier == code)
            return TOTPVerificationResult(true, periodNow - 1)

        return TOTPVerificationResult(codeNow == code, periodNow)
    }

    override fun period(config: IEncryptedTOTPConfiguration, instant: Instant): Long
            = instant.epochSecond / config.period.inWholeSeconds
}