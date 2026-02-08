package app.infrastructure.account

import app.Env
import app.infrastructure.models.account.Account
import app.infrastructure.models.account.TOTPConfiguration
import app.infrastructure.models.account.TOTPUsageTable
import dev.turingcomplete.kotlinonetimepassword.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AccountTOTPEngine {
    fun create(account: Account): TOTPConfiguration = transaction {
        val secret = GoogleAuthenticator.createRandomSecretAsByteArray()
        val encryptedSecret = encrypt(secret)
        Arrays.fill(secret, 0)

        val totpConfiguration = account.totpConfiguration ?: TOTPConfiguration.new {
            this.enabled = true
            this.account = account

            this.createdAt = System.currentTimeMillis()
            this.updatedAt = System.currentTimeMillis()

            this.digits = GoogleAuthenticator.CONFIG.codeDigits
            this.algorithm = GoogleAuthenticator.CONFIG.hmacAlgorithm.name
            this.periodSeconds = GoogleAuthenticator.CONFIG.run {
                timeStepUnit.toSeconds(timeStep)
            }

            this.encryptedSecret = ExposedBlob(encryptedSecret)
        }

        return@transaction totpConfiguration
    }

    fun consume(account: Account,
                code: String,
                currentMillis: Long = System.currentTimeMillis(),
                allowPreviousPeriodCode: Boolean = true): Boolean = transaction {
        fun accept(period: Long): Boolean {
            try {
                TOTPUsageTable.insert {
                    it[TOTPUsageTable.createdAt] = System.currentTimeMillis()
                    it[TOTPUsageTable.account] = account.id
                    it[TOTPUsageTable.period] = period
                }
            } catch (_: Exception) {
                return false
            }

            return true
        }

        val totpConfiguration = account.totpConfiguration
            ?: return@transaction false

        val totpSecret = decrypt(totpConfiguration.encryptedSecret.bytes)
        try {
            val generatorConfig = TimeBasedOneTimePasswordConfig(
                totpConfiguration.periodSeconds,
                TimeUnit.SECONDS,
                totpConfiguration.digits,
                HmacAlgorithm.valueOf(totpConfiguration.algorithm)
            )

            val totpGenerator = TimeBasedOneTimePasswordGenerator(totpSecret, generatorConfig)
            val period = totpGenerator.counter(currentMillis)

            val hotpGenerator = HmacOneTimePasswordGenerator(totpSecret, generatorConfig)
            val oldCode = hotpGenerator.generate(period - 1)
            val newCode = hotpGenerator.generate(period)

            if (code == newCode)
                return@transaction accept(period)

            if (code == oldCode && allowPreviousPeriodCode)
                return@transaction accept(period - 1)

            return@transaction false
        } finally {
            Arrays.fill(totpSecret, 0)
        }
    }

    fun generate(account: Account,
                 currentMillis: Long = System.currentTimeMillis()): String? = transaction {
        val totpConfiguration = account.totpConfiguration
            ?: return@transaction null

        val totpSecret = decrypt(totpConfiguration.encryptedSecret.bytes)
        try {
            val generatorConfig = TimeBasedOneTimePasswordConfig(
                totpConfiguration.periodSeconds,
                TimeUnit.SECONDS,
                totpConfiguration.digits,
                HmacAlgorithm.valueOf(totpConfiguration.algorithm)
            )

            val totpGenerator = TimeBasedOneTimePasswordGenerator(totpSecret, generatorConfig)
            return@transaction totpGenerator.generate(currentMillis)
        } finally {
            Arrays.fill(totpSecret, 0)
        }
    }

    private fun encrypt(value: ByteArray): ByteArray {
        val secureRandom = SecureRandom()
        val iv = ByteArray(16)
        secureRandom.nextBytes(iv)
        val ivspec = IvParameterSpec(iv)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(Env.ENCRYPTED_PARAMETER_SECRET.toCharArray(), Env.ENCRYPTED_PARAMETER_SALT.toByteArray(), 1, 256)
        val tmp = factory.generateSecret(spec)
        val secretKeySpec = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivspec)

        val cipherText = cipher.doFinal(value)
        val encryptedData = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, encryptedData, 0, iv.size)
        System.arraycopy(cipherText, 0, encryptedData, iv.size, cipherText.size)

        return encryptedData
    }

    private fun decrypt(value: ByteArray): ByteArray {
        val iv = value.copyOfRange(0, 16)
        val cipherText = value.copyOfRange(16, value.size)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(Env.ENCRYPTED_PARAMETER_SECRET.toCharArray(), Env.ENCRYPTED_PARAMETER_SALT.toByteArray(), 1, 256)
        val tmp = factory.generateSecret(spec)
        val secretKeySpec = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))

        return cipher.doFinal(cipherText)
    }
}