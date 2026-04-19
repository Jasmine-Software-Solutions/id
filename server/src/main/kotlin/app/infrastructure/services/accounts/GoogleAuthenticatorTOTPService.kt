package app.infrastructure.services.accounts

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISetTOTPConfiguration
import com.jasminesoftwaresolutions.id.domain.models.account.ITOTPConfiguration
import com.jasminesoftwaresolutions.id.domain.repositories.ITOTPConfigurationRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.ITOTPService
import com.jasminesoftwaresolutions.id.domain.services.accounts.TOTPVerificationResult
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
    private val repository: ITOTPConfigurationRepository<*>,
    private val defaultAlgorithm: HmacAlgorithm = HmacAlgorithm.SHA1,
    private val defaultPeriod: Duration = 30.seconds,
    private val defaultDigits: Int = 6
) : ITOTPService<ISetTOTPConfiguration> {
    companion object {
        private fun <T : ITOTPConfiguration> update(repository: ITOTPConfigurationRepository<T>, account: IAccount, function: T.() -> Unit) =
            repository.findByAccount(account).also { repository.update(it, function )}
    }

    override fun findByAccount(id: UUID): ISetTOTPConfiguration?
        = repository.findByAccount(id) as? ISetTOTPConfiguration

    override fun enable(account: IAccount): ISetTOTPConfiguration {
        val newSecret = GoogleAuthenticator.createRandomSecretAsByteArray()
        try {
            return update(repository, account) {
                this as ISetTOTPConfiguration

                this.enabled = true
                this.algorithm = defaultAlgorithm
                this.period = defaultPeriod
                this.digits = defaultDigits
                this.secret = newSecret
            } as ISetTOTPConfiguration
        } finally {
            Arrays.fill(newSecret, 0)
        }
    }

    override fun disable(account: IAccount): ITOTPConfiguration {
        return update(repository, account) {
            this.enabled = false
        }
    }

    override fun generate(config: ISetTOTPConfiguration, period: Long): Int {
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
        config: ISetTOTPConfiguration,
        period: Long,
        code: Int
    ): TOTPVerificationResult {
        val codeAtPeriod = generate(config, period)
        return TOTPVerificationResult(codeAtPeriod == code, period)
    }

    override fun verify(
        config: ISetTOTPConfiguration,
        code: Int
    ): TOTPVerificationResult {
        val periodNow = period(config, Instant.now())

        val codeNow = generate(config, periodNow)
        val codeEarlier = generate(config, periodNow - 1)

        if (codeEarlier == code)
            return TOTPVerificationResult(true, periodNow - 1)

        return TOTPVerificationResult(codeNow == code, periodNow)
    }

    override fun period(config: ISetTOTPConfiguration, instant: Instant): Long
            = instant.epochSecond / config.period.inWholeSeconds
}