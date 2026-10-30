package app.infrastructure.services.accounts

import com.jasminesoftwaresolutions.id.domain.models.account.IAccount
import com.jasminesoftwaresolutions.id.domain.models.account.ISetTOTPConfiguration
import com.jasminesoftwaresolutions.id.domain.models.account.ITOTPConfiguration
import com.jasminesoftwaresolutions.id.domain.repositories.ITOTPConfigurationRepository
import com.jasminesoftwaresolutions.id.domain.services.accounts.ITOTPService
import com.jasminesoftwaresolutions.id.domain.services.accounts.TOTPVerificationResult
import dev.turingcomplete.kotlinonetimepassword.*
import org.apache.commons.codec.binary.Base32
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GoogleAuthenticatorTOTPService(
    private val repository: ITOTPConfigurationRepository,
    private val issuer: String,
    private val defaultAlgorithm: HmacAlgorithm = HmacAlgorithm.SHA1,
    private val defaultPeriod: Duration = 30.seconds,
    private val defaultDigits: Int = 6,
) : ITOTPService {
    companion object {
        private fun update(repository: ITOTPConfigurationRepository, account: IAccount, function: ITOTPConfiguration.() -> Unit): ITOTPConfiguration {
            val config = repository.findByAccount(account)
            repository.update(config, function)
            return config
        }
    }

    override fun findByAccount(id: UUID): ITOTPConfiguration
        = repository.findByAccount(id)

    override fun enable(account: IAccount): ITOTPConfiguration {
        val newSecret = GoogleAuthenticator.createRandomSecretAsByteArray()
        try {
            update(repository, account) {
                this.enabled = true
            }

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
        val config = repository.findByAccount(account)
        repository.update(config) {
            this.enabled = false
        }
        return config
    }

    override fun generate(config: ITOTPConfiguration, period: Long): Int {
        val setConfig = config as ISetTOTPConfiguration
        val base32Secret = setConfig.secret.clone()
        val decodedSecret = Base32().decode(base32Secret)

        try {
            val generatorConfig = TimeBasedOneTimePasswordConfig(
                setConfig.period.inWholeSeconds,
                TimeUnit.SECONDS,
                setConfig.digits,
                setConfig.algorithm
            )

            val hotpGenerator = HmacOneTimePasswordGenerator(decodedSecret, generatorConfig)
            val code = hotpGenerator.generate(period)

            return Integer.valueOf(code)
        } finally {
            Arrays.fill(base32Secret, 0)
            Arrays.fill(decodedSecret, 0)
        }
    }

    override fun verify(
        config: ITOTPConfiguration,
        period: Long,
        code: Int
    ): TOTPVerificationResult {
        val codeAtPeriod = generate(config, period)
        return TOTPVerificationResult(codeAtPeriod == code, period)
    }

    override fun verify(
        config: ITOTPConfiguration,
        code: Int
    ): TOTPVerificationResult {
        val periodNow = period(config, Instant.now())

        val codeNow = generate(config, periodNow)
        val codeEarlier = generate(config, periodNow - 1)

        if (codeEarlier == code)
            return TOTPVerificationResult(true, periodNow - 1)

        return TOTPVerificationResult(codeNow == code, periodNow)
    }

    override fun period(config: ITOTPConfiguration, instant: Instant): Long
            = instant.epochSecond / (config as ISetTOTPConfiguration).period.inWholeSeconds

    override fun uri(config: ITOTPConfiguration): String {
        val setConfig = config as ISetTOTPConfiguration
        return OtpAuthUriBuilder
            .forTotp(setConfig.secret)
            .label(setConfig.account.email, issuer)
            .issuer(issuer)
            .algorithm(setConfig.algorithm)
            .digits(setConfig.digits)
            .period(setConfig.period.inWholeSeconds, TimeUnit.SECONDS)
            .buildToString()
    }
}
